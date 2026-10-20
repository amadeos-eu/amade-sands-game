package eu.amadeos.games.sands.domain;

import eu.amadeos.gameengine.ai.InfluenceMap;
import eu.amadeos.gameengine.tile.Cell;

import java.util.ArrayList;
import java.util.List;

/**
 * The computer's side, thinking once a second. It does what a player does, through the same methods of
 * the {@link Game} and with the same credits: it builds its base after a plan, keeps its harvesters at
 * work, makes an army, defends what is attacked, and now and then attacks. It does not see through the
 * fog more than a player does, with one exception: it knows where the enemy's buildings are, as if its
 * scouts had found them before the battle.
 *
 * <p>Where to attack it decides with an {@link InfluenceMap} of the enemy's strength: every armed vehicle
 * and turret spreads its strength around it, and the enemy building with the least strength around it is
 * the one worst defended.
 */
public final class Commander {
    /** Vehicles it keeps at most, harvesters not counted. */
    static final int ARMY = 24;
    /** It defends what is attacked within this many tiles of its yard. */
    static final int HOME = 20;

    private final Side side;
    private final Mission mission;
    private final Plan plan;
    private InfluenceMap influence;
    double nextAttack;
    int wave;
    int mixed;
    int failures;

    Commander(Side side, Mission mission, Plan plan) {
        this.side = side;
        this.mission = mission;
        this.plan = plan;
        this.nextAttack = mission.attack();
    }

    public Side side() {
        return side;
    }

    /** The enemy's strength as it saw it when it last chose where to attack; {@code null} before. */
    public InfluenceMap influence() {
        return influence;
    }

    /** When it attacks next, in game seconds, and how many attacks it has made. */
    public double nextAttack() {
        return nextAttack;
    }

    public int wave() {
        return wave;
    }

    // --8<-- [start:think]
    void think(Game game) {
        House house = game.house(side);
        placeReady(game, house);
        if (house.yard().isEmpty() && house.yard().ready() == null) {
            BuildingType next = nextBuilding(game, house);
            if (next != null) game.build(side, next);
        }
        if (house.factory().isEmpty()) {
            UnitType next = nextVehicle(game);
            if (next != null) game.produce(side, next);
        }
        List<Unit> idleHarvesters = new ArrayList<>();
        for (Unit unit : game.units()) {
            if (unit.side() == side && unit.type().harvests() && unit.task() == Task.IDLE) idleHarvesters.add(unit);
        }
        if (!idleHarvesters.isEmpty()) game.harvest(idleHarvesters, null);
        defend(game, house);
        attack(game);
    }
    // --8<-- [end:think]

    /** A building the yard has finished goes where it fits best; one that fits nowhere is given up after a while. */
    private void placeReady(Game game, House house) {
        BuildingType ready = house.yard().ready();
        if (ready == null) return;
        Cell spot = spot(game, ready);
        if (spot != null && game.place(side, spot.col(), spot.row()) == Game.Placement.OK) {
            failures = 0;
            return;
        }
        if (++failures > 20) {
            game.cancel(side, ready);
            failures = 0;
        }
    }

    // --8<-- [start:next]
    /**
     * The first building of the plan of which it has fewer than the plan names up to there; first a windtrap
     * if the power would not last. After the plan, turrets.
     */
    BuildingType nextBuilding(Game game, House house) {
        BuildingType windtrap = null;
        for (BuildingType type : plan.build()) if (type.power() > 0) windtrap = type;
        int spare = house.powerMade() - house.powerUsed();
        List<BuildingType> wanted = new ArrayList<>(plan.build());
        BuildingType turret = turret(game);
        if (turret != null) for (int i = 0; i < mission.turrets(); i++) wanted.add(turret);
        for (int i = 0; i < wanted.size(); i++) {
            BuildingType type = wanted.get(i);
            int planned = 0;
            for (int j = 0; j <= i; j++) if (wanted.get(j) == type) planned++;
            if (game.count(side, type) >= planned || !game.available(side, type)) continue;
            if (type.power() < 0 && spare + type.power() < 0 && windtrap != null && game.available(side, windtrap)) {
                return windtrap;
            }
            return type;
        }
        return spare < 0 && windtrap != null && game.available(side, windtrap) ? windtrap : null;
    }
    // --8<-- [end:next]

    private static BuildingType turret(Game game) {
        for (BuildingType type : game.rules().buildings()) if (type.weapon() != null && type.buildable()) return type;
        return null;
    }

    /** Harvesters first, as many as the plan wants for each refinery; then the army's vehicles in turn. */
    private UnitType nextVehicle(Game game) {
        UnitType harvester = game.rules().harvester();
        int refineries = 0;
        for (Building building : game.buildings()) {
            if (building.side() == side && building.type().dock() >= 0) refineries++;
        }
        int harvesters = 0;
        int army = 0;
        for (Unit unit : game.units()) {
            if (unit.side() != side) continue;
            if (unit.type().harvests()) harvesters++;
            else army++;
        }
        for (Carryall carryall : game.carryalls()) if (carryall.side() == side && carryall.cargo() != null) harvesters++;
        if (harvesters < refineries * plan.harvesters() && game.available(side, harvester)) return harvester;
        if (army >= ARMY) return null;
        for (int i = 0; i < plan.mix().size(); i++) {
            UnitType type = plan.mix().get(mixed % plan.mix().size());
            mixed++;
            if (game.available(side, type)) return type;
        }
        return null;
    }

    // --8<-- [start:spot]
    /**
     * Where a building fits: of all the tiles it may stand on, with a tile free all round it so that the
     * vehicles can pass, a turret the one nearest to the enemy's base, any other the one nearest to the yard.
     */
    Cell spot(Game game, BuildingType type) {
        Building yard = null;
        double enemyX = 0;
        double enemyY = 0;
        int enemies = 0;
        for (Building building : game.buildings()) {
            if (building.side() == side && building.type().yard() && yard == null) yard = building;
            if (building.side() != side) {
                enemyX += building.x();
                enemyY += building.y();
                enemies++;
            }
        }
        double homeX = yard != null ? yard.x() : enemyX / Math.max(1, enemies);
        double homeY = yard != null ? yard.y() : enemyY / Math.max(1, enemies);
        boolean turret = type.weapon() != null;
        double towardX = turret && enemies > 0 ? enemyX / enemies : homeX;
        double towardY = turret && enemies > 0 ? enemyY / enemies : homeY;
        Cell best = null;
        double bestScore = Double.MAX_VALUE;
        for (int row = 0; row < game.level().height(); row++) {
            for (int col = 0; col < game.level().width(); col++) {
                if (game.placement(side, type, col, row) != Game.Placement.OK) continue;
                if (!turret && !roomAround(game, type, col, row)) continue;
                double x = (col + type.width() / 2.0) * Field.TILE;
                double y = (row + type.height() / 2.0) * Field.TILE;
                double score = Math.hypot(x - towardX, y - towardY);
                if (turret) score += 0.5 * Math.hypot(x - homeX, y - homeY);
                if (score < bestScore) {
                    best = new Cell(col, row);
                    bestScore = score;
                }
            }
        }
        return best;
    }
    // --8<-- [end:spot]

    /** Whether the tiles all round a building's place are free of buildings, and not cliffs. */
    private static boolean roomAround(Game game, BuildingType type, int col, int row) {
        for (int r = row - 1; r <= row + type.height(); r++) {
            for (int c = col - 1; c <= col + type.width(); c++) {
                boolean inside = c >= col && r >= row && c < col + type.width() && r < row + type.height();
                if (inside || !game.contains(c, r)) continue;
                if (game.building(c, r) != null || !game.level().ground(c, r).drivable()) return false;
            }
        }
        return true;
    }

    /** What is attacked near the base gets every idle vehicle there that is not out attacking. */
    private void defend(Game game, House house) {
        if (game.time() - house.alarm > Field.ALARM_SECONDS) return;
        Thing attacker = game.thing(house.attacker);
        if (attacker == null || attacker.side() == side || !game.visible(side, attacker)) return;
        Building yard = null;
        for (Building building : game.buildings()) if (building.side() == side && building.type().yard()) yard = building;
        if (yard != null && Math.hypot(yard.x() - house.attackedX, yard.y() - house.attackedY) > HOME * Field.TILE) return;
        List<Unit> defenders = new ArrayList<>();
        for (Unit unit : game.units()) {
            if (unit.side() == side && unit.type().armed() && unit.task() == Task.IDLE) defenders.add(unit);
        }
        if (!defenders.isEmpty()) game.attack(defenders, attacker);
    }

    // --8<-- [start:attack]
    /**
     * When the time has come and the army is large enough, as many idle armed vehicles as this attack
     * needs go hunting towards the enemy building that is defended worst.
     */
    private void attack(Game game) {
        if (game.time() < nextAttack) return;
        List<Unit> army = new ArrayList<>();
        for (Unit unit : game.units()) {
            if (unit.side() == side && unit.type().armed() && unit.task() == Task.IDLE) army.add(unit);
        }
        int needed = Math.min(mission.army() + wave * mission.grows(), ARMY);
        if (army.size() < needed) return;
        Cell target = weakest(game);
        if (target == null) return;
        // The vehicles made first go; the rest stay at home.
        game.hunt(army.subList(0, needed), target);
        wave++;
        nextAttack = game.time() + mission.every();
    }

    /** The enemy building with the least of the enemy's strength around it. */
    Cell weakest(Game game) {
        InfluenceMap map = new InfluenceMap(game.level().width(), game.level().height());
        for (Unit unit : game.units()) {
            Weapon weapon = unit.type().weapon();
            if (unit.side() == side || weapon == null) continue;
            Cell cell = unit.cell();
            map.add(cell.col(), cell.row(), weapon.damage() / weapon.reload(), (int) Math.ceil(weapon.range()) + 2);
        }
        List<Cell> candidates = new ArrayList<>();
        for (Building building : game.buildings()) {
            if (building.side() == side) continue;
            Weapon weapon = building.type().weapon();
            if (weapon != null) {
                map.add(building.centre().col(), building.centre().row(), weapon.damage() / weapon.reload(),
                        (int) Math.ceil(weapon.range()) + 2);
            }
            candidates.add(building.centre());
        }
        influence = map;
        return map.weakest(candidates);
    }
    // --8<-- [end:attack]
}
