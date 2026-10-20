package eu.amadeos.games.sands.domain;

import eu.amadeos.gameengine.collision.Grid;
import eu.amadeos.gameengine.economy.Production;
import eu.amadeos.gameengine.fog.FogOfWar;
import eu.amadeos.gameengine.math.Angles;
import eu.amadeos.gameengine.math.Vector;
import eu.amadeos.gameengine.path.FieldCache;
import eu.amadeos.gameengine.path.FlowField;
import eu.amadeos.gameengine.path.Formation;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.tile.Cell;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A battle on a mission's map: the two houses, their buildings and vehicles, the shots, the crystal, the burrower
 * and the airlifters, and the rules of each step. It knows nothing of the screen; the player's orders come
 * in through its methods, the computer's from its {@link Commander}s, and what happened goes out as
 * {@link Event}s. Everything that happens by chance comes from one seeded generator, and nothing depends
 * on the order of a hash map, so a battle saved and loaded goes on exactly as it would have.
 */
public final class Game {
    private static final int[] DX = {0, 1, 0, -1, 1, 1, -1, -1};
    private static final int[] DY = {-1, 0, 1, 0, -1, 1, 1, -1};

    /** How the battle stands for the blue side. */
    public enum Phase { PLAYING, WON, LOST }

    /** Whether a building may stand on a tile, and why not. */
    public enum Placement { OK, NOT_READY, OUTSIDE, NOT_ROCK, OCCUPIED, TOO_FAR }

    private final Level level;
    private final Rules rules;
    private final int width;
    private final int height;
    SeededRandom random;
    final List<Unit> units = new ArrayList<>();
    final List<Building> buildings = new ArrayList<>();
    final List<Shot> shots = new ArrayList<>();
    final List<Airlifter> airlifters = new ArrayList<>();
    Burrower burrower;
    /** Every thing by its id; only looked up, never walked through, so its order does not matter. */
    private final Map<Integer, Thing> things = new HashMap<>();
    private final Map<Cell, Cell> approaches = new HashMap<>();
    /** The building on each tile, 0 for none; and whether the tile is its wall, not its pad. */
    private final int[] occupant;
    private final boolean[] wall;
    final int[] crystal;
    private int crystalVersion;
    private int version;
    private final FieldCache fields;
    final EnumMap<Side, House> houses = new EnumMap<>(Side.class);
    final EnumMap<Side, Commander> commanders = new EnumMap<>(Side.class);
    private final Grid<Unit> crowd;
    private final List<Event> events = new ArrayList<>();
    double time;
    double thought;
    int nextId = 1;
    Phase phase = Phase.PLAYING;
    Side winner;

    /** A new battle on {@code level}; the {@code computer} sides are played by commanders. */
    public Game(Level level, Rules rules, long seed, Set<Side> computer) {
        this(level, rules, seed, computer, true);
    }

    Game(Level level, Rules rules, long seed, Set<Side> computer, boolean populate) {
        this.level = level;
        this.rules = rules;
        this.width = level.width();
        this.height = level.height();
        this.random = new SeededRandom(seed);
        this.occupant = new int[width * height];
        this.wall = new boolean[width * height];
        this.crystal = new int[width * height];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) crystal[row * width + col] = level.ground(col, row).crystal();
        }
        this.fields = new FieldCache(width, height, this::open);
        this.crowd = new Grid<>(width * Field.TILE, height * Field.TILE, 2 * Field.TILE);
        Mission mission = level.mission();
        houses.put(Side.BLUE, new House(Side.BLUE, mission.credits(), width, height));
        houses.put(Side.RED, new House(Side.RED, mission.enemyCredits(), width, height));
        for (Side side : computer) commanders.put(side, new Commander(side, mission, rules.plan()));
        if (!populate) return;
        for (Level.Start start : level.starts()) {
            if (start.building()) {
                add(rules.building(start.kind()), start.side(), start.col(), start.row());
            } else {
                Unit unit = add(rules.unit(start.kind()), start.side(), (start.col() + 0.5) * Field.TILE,
                        (start.row() + 0.5) * Field.TILE, Math.PI / 2);
                if (unit.type().harvests()) unit.task = Task.HARVEST;
            }
        }
        account();
        look();
    }

    // ---- what there is --------------------------------------------------------------------------

    public Level level() {
        return level;
    }

    public Rules rules() {
        return rules;
    }

    /** Seconds since the battle began. */
    public double time() {
        return time;
    }

    public Phase phase() {
        return phase;
    }

    /** The side with the only buildings left; {@code null} while both have some. */
    public Side winner() {
        return winner;
    }

    /** What happened in the last step. */
    public List<Event> events() {
        return Collections.unmodifiableList(events);
    }

    public List<Unit> units() {
        return Collections.unmodifiableList(units);
    }

    public List<Building> buildings() {
        return Collections.unmodifiableList(buildings);
    }

    public List<Shot> shots() {
        return Collections.unmodifiableList(shots);
    }

    public List<Airlifter> airlifters() {
        return Collections.unmodifiableList(airlifters);
    }

    /** The burrower, or {@code null} before it shows or on a mission without one. */
    public Burrower burrower() {
        return burrower;
    }

    public House house(Side side) {
        return houses.get(side);
    }

    /** The computer playing a side, or {@code null} for the player's. */
    public Commander commander(Side side) {
        return commanders.get(side);
    }

    /** A thing by its id, or {@code null} once it is gone. */
    public Thing thing(int id) {
        return things.get(id);
    }

    /** The credits of crystal left on a tile. */
    public int crystal(int col, int row) {
        return contains(col, row) ? crystal[row * width + col] : 0;
    }

    /** Changes whenever crystal is taken: the ground is drawn again then. */
    public int crystalVersion() {
        return crystalVersion;
    }

    /** Changes whenever a building goes up or comes down. */
    public int version() {
        return version;
    }

    public boolean contains(int col, int row) {
        return col >= 0 && row >= 0 && col < width && row < height;
    }

    // --8<-- [start:open]
    /** Whether a vehicle may drive onto a tile: anything but cliffs and the walls of buildings. */
    public boolean open(int col, int row) {
        if (!contains(col, row)) return false;
        int i = row * width + col;
        return level.ground(col, row).drivable() && !wall[i];
    }
    // --8<-- [end:open]

    /** The building on a tile, or {@code null}. */
    public Building building(int col, int row) {
        if (!contains(col, row)) return null;
        int id = occupant[row * width + col];
        return id == 0 ? null : (Building) things.get(id);
    }

    /** The vehicle of any side under a point of the world, the one nearest to it, or {@code null}. */
    public Unit unitAt(double x, double y) {
        Unit best = null;
        double nearest = Double.MAX_VALUE;
        for (Unit unit : units) {
            double d = Math.hypot(unit.x - x, unit.y - y);
            if (d <= unit.radius() + 2 && d < nearest) {
                best = unit;
                nearest = d;
            }
        }
        return best;
    }

    /** Whether {@code side} sees a thing now: a vehicle on a visible tile, a building with one. */
    public boolean visible(Side side, Thing thing) {
        FogOfWar fog = houses.get(side).fog;
        if (thing instanceof Building building) {
            for (int dy = 0; dy < building.type().height(); dy++) {
                for (int dx = 0; dx < building.type().width(); dx++) {
                    if (fog.visible(building.col() + dx, building.row() + dy)) return true;
                }
            }
            return false;
        }
        return fog.visible((int) Math.floor(thing.x() / Field.TILE), (int) Math.floor(thing.y() / Field.TILE));
    }

    // ---- the player's and the computer's orders -------------------------------------------------

    // --8<-- [start:move]
    /**
     * Sends vehicles to a tile. Each gets a slot of its own near it, the one that suits it best, and they
     * all follow one flow field towards the tile.
     */
    public void move(Collection<Unit> group, Cell target) {
        send(group, target, Task.MOVE);
    }

    private void send(Collection<Unit> group, Cell target, Task task) {
        List<Unit> vehicles = group.stream().filter(Unit::alive).toList();
        if (vehicles.isEmpty() || !contains(target.col(), target.row())) return;
        List<Cell> slots = Formation.slots(target, vehicles.size(), width, height, this::open);
        double[] xs = new double[vehicles.size()];
        double[] ys = new double[vehicles.size()];
        for (int i = 0; i < vehicles.size(); i++) {
            xs[i] = vehicles.get(i).x / Field.TILE;
            ys[i] = vehicles.get(i).y / Field.TILE;
        }
        int[] slotOf = Formation.assign(xs, ys, slots);
        // The field leads to the tile clicked; a building's or a cliff's is closed, so then to the slot nearest to it.
        Cell way = open(target.col(), target.row()) || slots.isEmpty() ? target : slots.getFirst();
        for (int i = 0; i < vehicles.size(); i++) {
            Unit unit = vehicles.get(i);
            Cell slot = slotOf[i] >= 0 ? slots.get(slotOf[i]) : target;
            release(unit);
            unit.task = task;
            unit.goal = slot;
            unit.way = way;
            unit.guard = slot;
            unit.target = 0;
            unit.stuck = 0;
        }
    }
    // --8<-- [end:move]

    /** Sends vehicles to a tile, fighting all they meet on the way. */
    public void hunt(Collection<Unit> group, Cell target) {
        send(group, target, Task.HUNT);
    }

    /** The armed vehicles go after {@code enemy}; the others drive near it. */
    public void attack(Collection<Unit> group, Thing enemy) {
        List<Unit> unarmed = new ArrayList<>();
        for (Unit unit : group) {
            if (!unit.alive() || unit.side() == enemy.side()) continue;
            if (!unit.type().armed()) {
                unarmed.add(unit);
                continue;
            }
            release(unit);
            unit.task = Task.ATTACK;
            unit.target = enemy.id();
            unit.goal = null;
            unit.stuck = 0;
        }
        if (!unarmed.isEmpty()) move(unarmed, cellOf(enemy.x(), enemy.y()));
    }

    /** Harvesters go to the crystal at {@code field}, or the nearest there is for {@code null}; others drive there. */
    public void harvest(Collection<Unit> group, Cell field) {
        List<Unit> others = new ArrayList<>();
        for (Unit unit : group) {
            if (!unit.alive()) continue;
            if (!unit.type().harvests()) {
                others.add(unit);
                continue;
            }
            release(unit);
            unit.task = Task.HARVEST;
            unit.field = field;
            unit.goal = field != null && crystal(field.col(), field.row()) > 0 ? field : null;
            unit.stuck = 0;
        }
        if (!others.isEmpty() && field != null) move(others, field);
    }

    /** Harvesters drive home with what they carry. */
    public void bringHome(Collection<Unit> group) {
        for (Unit unit : group) {
            if (!unit.alive() || !unit.type().harvests() || unit.load == 0) continue;
            release(unit);
            unit.task = Task.RETURN;
            unit.goal = null;
        }
    }

    /** Vehicles stop where they are and stand guard there. */
    public void stop(Collection<Unit> group) {
        for (Unit unit : group) {
            if (!unit.alive()) continue;
            release(unit);
            unit.task = Task.IDLE;
            unit.goal = null;
            unit.target = 0;
            unit.guard = unit.cell();
        }
    }

    /** A harvester that leaves a refinery's pad frees it. */
    private void release(Unit unit) {
        unit.harvesting = false;
        if (unit.task != Task.UNLOAD) return;
        for (Building building : buildings) if (building.docked == unit.id()) building.docked = 0;
    }

    // --8<-- [start:available]
    /** Whether a side has the buildings a kind needs, and a yard to build it with. */
    public boolean available(Side side, BuildingType type) {
        return type.buildable() && has(side, BuildingType::yard) && type.needs().stream().allMatch(id -> has(side, id));
    }

    /** Whether a side has a factory, and the buildings a vehicle needs. */
    public boolean available(Side side, UnitType type) {
        return has(side, BuildingType::factory) && type.needs().stream().allMatch(id -> has(side, id));
    }
    // --8<-- [end:available]

    private boolean has(Side side, String id) {
        for (Building building : buildings) if (building.side() == side && building.type().id().equals(id)) return true;
        return false;
    }

    private boolean has(Side side, java.util.function.Predicate<BuildingType> test) {
        for (Building building : buildings) if (building.side() == side && test.test(building.type())) return true;
        return false;
    }

    /** How many buildings of a kind a side has. */
    public int count(Side side, BuildingType type) {
        int n = 0;
        for (Building building : buildings) if (building.side() == side && building.type() == type) n++;
        return n;
    }

    /** Whether a side sees the minimap: with an outpost, and the power it needs. */
    public boolean radar(Side side) {
        if (houses.get(side).lowPower()) return false;
        for (Building building : buildings) if (building.side() == side && building.type().radar()) return true;
        return false;
    }

    /** Orders a building from the yard; false if the side cannot build it yet. */
    public boolean build(Side side, BuildingType type) {
        if (!available(side, type)) return false;
        houses.get(side).yard.order(type, type.cost(), type.seconds());
        return true;
    }

    /** Orders a vehicle from the factory; false if the side cannot make it yet. */
    public boolean produce(Side side, UnitType type) {
        if (!available(side, type)) return false;
        houses.get(side).factory.order(type, type.cost(), type.seconds());
        return true;
    }

    /** Takes back the last order of a building, with what was paid for it; a finished one waiting is refunded whole. */
    public boolean cancel(Side side, BuildingType type) {
        House house = houses.get(side);
        if (house.yard.ready() == type && house.yard.count(type) == 0) {
            house.yard.take();
            house.credits.refund(type.cost());
            return true;
        }
        return house.yard.cancel(type, house.credits);
    }

    public boolean cancel(Side side, UnitType type) {
        House house = houses.get(side);
        return house.factory.cancel(type, house.credits);
    }

    // --8<-- [start:placement]
    /**
     * Whether the building the yard has ready may stand with its top left corner on a tile: on rock, on no
     * other building and under no vehicle, and near a building of its side.
     */
    public Placement placement(Side side, BuildingType type, int col, int row) {
        for (int dy = 0; dy < type.height(); dy++) {
            for (int dx = 0; dx < type.width(); dx++) {
                int c = col + dx;
                int r = row + dy;
                if (!contains(c, r)) return Placement.OUTSIDE;
                if (!level.ground(c, r).buildable()) return Placement.NOT_ROCK;
                if (occupant[r * width + c] != 0) return Placement.OCCUPIED;
            }
        }
        for (Unit unit : units) {
            double left = col * Field.TILE - unit.radius() + 1;
            double top = row * Field.TILE - unit.radius() + 1;
            if (unit.x > left && unit.y > top && unit.x < left + type.width() * Field.TILE + 2 * unit.radius() - 2
                    && unit.y < top + type.height() * Field.TILE + 2 * unit.radius() - 2) {
                return Placement.OCCUPIED;
            }
        }
        for (Building building : buildings) {
            if (building.side() != side) continue;
            int gapX = Math.max(building.col() - (col + type.width()), col - (building.col() + building.type().width()));
            int gapY = Math.max(building.row() - (row + type.height()), row - (building.row() + building.type().height()));
            if (Math.max(gapX, gapY) < Field.REACH) return Placement.OK;
        }
        return Placement.TOO_FAR;
    }
    // --8<-- [end:placement]

    /** Places the building the yard has ready; a refinery's harvester is flown in. */
    public Placement place(Side side, int col, int row) {
        House house = houses.get(side);
        BuildingType type = house.yard.ready();
        if (type == null) return Placement.NOT_READY;
        Placement result = placement(side, type, col, row);
        if (result != Placement.OK) return result;
        house.yard.take();
        Building building = add(type, side, col, row);
        account();
        events.add(new Event.Placed(building));
        if (type.dock() >= 0) fly(building);
        return Placement.OK;
    }

    /** Sells a building for part of its price. */
    public boolean sell(Building building) {
        if (!buildings.contains(building)) return false;
        int refund = (int) Math.round(building.type().cost() * Field.REFUND);
        houses.get(building.side()).credits.refund(refund);
        remove(building);
        events.add(new Event.Sold(building, refund));
        account();
        return true;
    }

    // ---- the step -------------------------------------------------------------------------------

    // --8<-- [start:update]
    public void update(double seconds) {
        events.clear();
        time += seconds;
        for (Unit unit : units) unit.remember();
        for (Building building : buildings) building.lastTurret = building.turret;
        for (Shot shot : shots) shot.remember();
        for (Airlifter airlifter : airlifters) airlifter.remember();
        if (burrower != null) burrower.remember();

        produce(seconds);
        thought += seconds;
        if (thought >= Field.THINK_SECONDS) {
            thought -= Field.THINK_SECONDS;
            for (Commander commander : commanders.values()) commander.think(this);
        }
        for (int i = 0; i < units.size(); i++) behave(units.get(i), seconds);
        separate();
        for (Building building : buildings) defend(building, seconds);
        fly(seconds);
        carry(seconds);
        if (level.mission().burrower()) burrower(seconds);
        bury();
        look();
        judge();
    }
    // --8<-- [end:update]

    /** The yard and the factory of each side work, at half speed on low power, not at all without the building. */
    private void produce(double seconds) {
        for (House house : houses.values()) {
            Side side = house.side();
            double speed = house.lowPower() ? Field.LOW_POWER : 1;
            BuildingType built = house.yard.update(seconds, house.credits, has(side, BuildingType::yard) ? speed : 0);
            if (built != null) {
                house.yard.keep(built);
                events.add(new Event.Ready(side, built));
            }
            UnitType made = house.factory.update(seconds, house.credits, has(side, BuildingType::factory) ? speed : 0);
            if (made != null) deploy(side, made);
        }
    }

    /** A new vehicle drives out of the factory's door, below it. */
    private void deploy(Side side, UnitType type) {
        Building factory = null;
        for (Building building : buildings) {
            if (building.side() == side && building.type().factory()) {
                factory = building;
                break;
            }
        }
        if (factory == null) return;
        Cell door = new Cell(factory.col() + factory.type().width() / 2, factory.row() + factory.type().height());
        if (!contains(door.col(), door.row())) door = factory.centre();
        List<Cell> slots = Formation.slots(door, 1, width, height, this::open);
        if (slots.isEmpty()) return;
        Cell slot = slots.getFirst();
        Unit unit = add(type, side, (slot.col() + 0.5) * Field.TILE, (slot.row() + 0.5) * Field.TILE, Math.PI / 2);
        unit.guard = slot;
        if (type.harvests()) unit.task = Task.HARVEST;
        houses.get(side).made++;
        events.add(new Event.Deployed(unit));
    }

    // --8<-- [start:behave]
    /** What a vehicle does in a step, by its task. */
    private void behave(Unit unit, double seconds) {
        unit.reload -= seconds;
        unit.sinceShot += seconds;
        unit.moving = false;
        if (unit.guard == null) unit.guard = unit.cell();
        switch (unit.task) {
            case IDLE -> guard(unit, seconds);
            case MOVE -> {
                if (drive(unit, unit.goal, unit.way, seconds)) settle(unit);
                if (unit.type().turret()) fireOnTheWay(unit, seconds);
            }
            case ATTACK -> {
                Thing enemy = things.get(unit.target);
                if (enemy == null || !enemy.alive() || !visible(unit.side(), enemy)) settle(unit);
                else engage(unit, enemy, seconds);
            }
            case HUNT -> {
                Thing enemy = nearestEnemy(unit, unit.type().sight() * Field.TILE, true);
                if (enemy != null) engage(unit, enemy, seconds);
                else if (drive(unit, unit.goal, unit.way, seconds)) settle(unit);
            }
            case HARVEST -> harvest(unit, seconds);
            case RETURN -> bringHome(unit, seconds);
            case UNLOAD -> unload(unit, seconds);
        }
        if (unit.type().turret() && unit.sinceShot > 2) {
            unit.turret = Angles.turnTowards(unit.turret, unit.angle, Field.TURRET_TURN * seconds * 0.5);
        } else if (!unit.type().turret()) {
            unit.turret = unit.angle;
        }
    }
    // --8<-- [end:behave]

    /** Stops and stands guard where it is. */
    private void settle(Unit unit) {
        unit.task = Task.IDLE;
        unit.goal = null;
        unit.way = null;
        unit.target = 0;
        unit.guard = unit.cell();
    }

    // --8<-- [start:guard]
    /** Standing guard: an enemy in sight is chased, but no further than the leash; then back to the spot. */
    private void guard(Unit unit, double seconds) {
        if (unit.type().armed()) {
            Thing enemy = nearestEnemy(unit, unit.type().sight() * Field.TILE, true);
            double leash = (Field.LEASH + unit.type().weapon().range()) * Field.TILE;
            double gx = (unit.guard.col() + 0.5) * Field.TILE;
            double gy = (unit.guard.row() + 0.5) * Field.TILE;
            if (enemy != null && gap(gx, gy, enemy) <= leash) {
                engage(unit, enemy, seconds);
                return;
            }
        }
        double gx = (unit.guard.col() + 0.5) * Field.TILE;
        double gy = (unit.guard.row() + 0.5) * Field.TILE;
        if (Math.hypot(unit.x - gx, unit.y - gy) > Field.TILE) drive(unit, unit.guard, seconds);
    }
    // --8<-- [end:guard]

    /** In range: aim and fire; out of range: drive closer. */
    private void engage(Unit unit, Thing enemy, double seconds) {
        Weapon weapon = unit.type().weapon();
        if (weapon == null) return;
        if (gap(unit.x, unit.y, enemy) <= weapon.range() * Field.TILE) {
            aim(unit, enemy, seconds);
        } else {
            drive(unit, approach(enemy), seconds);
            if (unit.type().turret()) aim(unit, enemy, seconds);
        }
    }

    /**
     * The tile to drive to, to fight a thing: its own, or for a building, whose tiles are closed, the open
     * tile nearest to its middle. Kept until a building goes up or comes down.
     */
    private Cell approach(Thing enemy) {
        Cell cell = cellOf(enemy.x(), enemy.y());
        if (open(cell.col(), cell.row())) return cell;
        return approaches.computeIfAbsent(cell, c -> {
            List<Cell> slots = Formation.slots(c, 1, width, height, this::open);
            return slots.isEmpty() ? c : slots.getFirst();
        });
    }

    /** A vehicle whose gun turns fires at what comes in range while it drives. */
    private void fireOnTheWay(Unit unit, double seconds) {
        Thing enemy = nearestEnemy(unit, unit.type().weapon().range() * Field.TILE, false);
        if (enemy != null) aim(unit, enemy, seconds);
    }

    /** Turns the gun, or the whole vehicle, towards the enemy, and fires when it points there and is loaded. */
    private void aim(Unit unit, Thing enemy, double seconds) {
        double want = Math.atan2(enemy.y() - unit.y, enemy.x() - unit.x);
        boolean aimed;
        if (unit.type().turret()) {
            unit.turret = Angles.turnTowards(unit.turret, want, Field.TURRET_TURN * seconds);
            aimed = Math.abs(Angles.difference(unit.turret, want)) < Field.AIMED;
        } else {
            if (!unit.moving) unit.angle = Angles.turnTowards(unit.angle, want, Field.TURN * seconds);
            unit.turret = unit.angle;
            aimed = Math.abs(Angles.difference(unit.angle, want)) < Field.AIMED;
        }
        Weapon weapon = unit.type().weapon();
        if (!aimed || unit.reload > 0 || gap(unit.x, unit.y, enemy) > weapon.range() * Field.TILE) return;
        unit.reload = weapon.reload();
        unit.sinceShot = 0;
        double muzzle = unit.radius() + 2;
        fire(weapon, unit.side(), unit.id(), unit.x + Math.cos(unit.turret) * muzzle,
                unit.y + Math.sin(unit.turret) * muzzle, enemy);
    }

    // --8<-- [start:fire]
    private void fire(Weapon weapon, Side side, int shooter, double x, double y, Thing enemy) {
        double toX = enemy.x();
        double toY = enemy.y();
        int target = enemy.id();
        if (weapon.kind() == Weapon.Kind.ROCKET) {
            // A rocket flies to a point near its target, chosen now: it may miss a vehicle that drives on.
            double a = random.nextDouble() * 2 * Math.PI;
            double d = random.nextDouble() * Field.SCATTER * Field.TILE;
            toX += Math.cos(a) * d;
            toY += Math.sin(a) * d;
            target = 0;
        }
        shots.add(new Shot(weapon, side, shooter, target, x, y, toX, toY));
        events.add(new Event.Fired(x, y, Math.atan2(toY - y, toX - x), weapon.kind(), side));
    }
    // --8<-- [end:fire]

    /** The nearest enemy the side sees within {@code reach} units: vehicles first, buildings if asked. */
    private Thing nearestEnemy(Unit unit, double reach, boolean buildingsToo) {
        Thing best = null;
        double nearest = Double.MAX_VALUE;
        for (Unit other : units) {
            if (other.side() == unit.side() || !other.alive()) continue;
            double d = gap(unit.x, unit.y, other);
            if (d <= reach && d < nearest && visible(unit.side(), other)) {
                best = other;
                nearest = d;
            }
        }
        if (best != null || !buildingsToo) return best;
        for (Building building : buildings) {
            if (building.side() == unit.side() || !building.alive()) continue;
            double d = building.distance(unit.x, unit.y);
            if (d <= reach && d < nearest && visible(unit.side(), building)) {
                best = building;
                nearest = d;
            }
        }
        return best;
    }

    /** How far a point is from the edge of a thing, in units. */
    static double gap(double x, double y, Thing thing) {
        if (thing instanceof Building building) return building.distance(x, y);
        return Math.max(0, Math.hypot(thing.x() - x, thing.y() - y) - thing.radius());
    }

    // --8<-- [start:drive]
    private boolean drive(Unit unit, Cell goal, double seconds) {
        return drive(unit, goal, null, seconds);
    }

    /**
     * Drives a vehicle a step towards a tile, its slot; true when it has arrived. Near the slot, with
     * nothing in the way, it drives straight there. Else it follows the flow field towards {@code way},
     * the tile its group was sent to, which all of the group share and the cache keeps; once there, or
     * without a group, the field towards the slot itself. It turns first and drives when it points the
     * right way.
     */
    private boolean drive(Unit unit, Cell goal, Cell way, double seconds) {
        if (goal == null) return true;
        double gx = (goal.col() + 0.5) * Field.TILE;
        double gy = (goal.row() + 0.5) * Field.TILE;
        double distance = Math.hypot(gx - unit.x, gy - unit.y);
        if (distance < Field.ARRIVED) return true;
        Vector heading;
        double tx = unit.x / Field.TILE;
        double ty = unit.y / Field.TILE;
        if (distance < Field.DIRECT * Field.TILE
                && Formation.clearLine(tx, ty, gx / Field.TILE, gy / Field.TILE, width, height, this::open)) {
            heading = new Vector(gx - unit.x, gy - unit.y).normalized();
        } else {
            Cell via = way;
            if (via == null || Math.hypot((via.col() + 0.5) * Field.TILE - unit.x,
                    (via.row() + 0.5) * Field.TILE - unit.y) < 1.5 * Field.TILE) {
                via = goal;
            }
            FlowField field = fields.toward(via);
            heading = field.heading(tx, ty);
            if (heading.lengthSquared() == 0) {
                // On the field's goal but not yet at the slot; or the goal cannot be reached at all.
                if (!field.reaches((int) Math.floor(tx), (int) Math.floor(ty))) return true;
                heading = new Vector(gx - unit.x, gy - unit.y).normalized();
            }
        }
        double want = heading.angle();
        unit.angle = Angles.turnTowards(unit.angle, want, Field.TURN * seconds);
        double off = Math.abs(Angles.difference(unit.angle, want));
        if (off > 1.2) return false;
        Ground ground = level.ground((int) Math.floor(tx), (int) Math.floor(ty));
        double speed = unit.type().speed() * Field.TILE * (ground == Ground.DUNE ? Field.DUNE_SPEED : 1);
        double step = Math.min(distance, speed * seconds * Math.cos(off));
        double beforeX = unit.x;
        double beforeY = unit.y;
        nudge(unit, Math.cos(unit.angle) * step, Math.sin(unit.angle) * step);
        double moved = Math.hypot(unit.x - beforeX, unit.y - beforeY);
        unit.moving = moved > 0;
        // A vehicle that hardly moves, though it means to, gives up near the goal, and in the end anywhere.
        unit.stuck = moved < step * 0.25 ? unit.stuck + seconds : Math.max(0, unit.stuck - seconds);
        return unit.stuck > 1.5 && distance < 2.5 * Field.TILE || unit.stuck > 5;
    }
    // --8<-- [end:drive]

    /** Moves a vehicle, but not onto a tile it may not enter: along the other axis, if one is free. */
    private void nudge(Unit unit, double dx, double dy) {
        double maxX = width * Field.TILE - 1;
        double maxY = height * Field.TILE - 1;
        double nx = Math.clamp(unit.x + dx, 1, maxX);
        if (openAt(nx, unit.y)) unit.x = nx;
        double ny = Math.clamp(unit.y + dy, 1, maxY);
        if (openAt(unit.x, ny)) unit.y = ny;
    }

    private boolean openAt(double x, double y) {
        return open((int) Math.floor(x / Field.TILE), (int) Math.floor(y / Field.TILE));
    }

    // --8<-- [start:separate]
    /**
     * Vehicles that overlap push each other apart, the moving ones more than those that stand: a group
     * spreads out instead of driving into one point, and a harvester at work is not shoved off its crystal.
     */
    private void separate() {
        crowd.clear();
        for (Unit unit : units) crowd.add(unit, unit.x, unit.y, unit.radius());
        for (Unit unit : units) {
            crowd.forEachNear(unit.x, unit.y, unit.radius(), other -> {
                if (other.id() <= unit.id()) return;
                double dx = other.x - unit.x;
                double dy = other.y - unit.y;
                double d = Math.hypot(dx, dy);
                double least = (unit.radius() + other.radius()) * 0.8;
                if (d >= least) return;
                if (d < 1e-6) {
                    dx = 1;
                    dy = 0;
                    d = 1;
                }
                double a = weight(unit);
                double b = weight(other);
                if (a + b == 0) return;
                double push = Math.min(least - d, 2) * 0.5;
                nudge(unit, -dx / d * push * a / (a + b), -dy / d * push * a / (a + b));
                nudge(other, dx / d * push * b / (a + b), dy / d * push * b / (a + b));
            });
        }
    }
    // --8<-- [end:separate]

    private static double weight(Unit unit) {
        if (unit.task == Task.UNLOAD || unit.harvesting) return 0;
        return unit.moving ? 1 : 0.3;
    }

    // --8<-- [start:harvest]
    /** A harvester takes in crystal where it stands, or looks for the nearest; full, it drives home. */
    private void harvest(Unit unit, double seconds) {
        unit.harvesting = false;
        if (unit.load >= unit.type().capacity()) {
            unit.task = Task.RETURN;
            unit.goal = null;
            return;
        }
        Cell here = unit.cell();
        int i = here.row() * width + here.col();
        boolean there = unit.goal == null || Math.abs(unit.goal.col() - here.col()) <= 1
                && Math.abs(unit.goal.row() - here.row()) <= 1;
        if (crystal[i] > 0 && there) {
            unit.harvesting = true;
            unit.field = here;
            unit.work += Field.HARVEST_RATE * seconds;
            int take = Math.min(Math.min((int) unit.work, crystal[i]), unit.type().capacity() - unit.load);
            if (take > 0) {
                unit.work -= take;
                crystal[i] -= take;
                unit.load += take;
                crystalVersion++;
            }
            if (crystal[i] == 0) unit.goal = null;
            return;
        }
        if (unit.goal == null || crystal(unit.goal.col(), unit.goal.row()) == 0) {
            unit.goal = findCrystal(unit.field != null ? unit.field : here, unit);
            if (unit.goal == null) {
                if (unit.load > 0) unit.task = Task.RETURN;
                else settle(unit);
                return;
            }
        }
        if (drive(unit, unit.goal, seconds)) {
            if (crystal(unit.goal.col(), unit.goal.row()) == 0 || unit.stuck > 1.5) unit.goal = null;
            unit.stuck = 0;
        }
    }
    // --8<-- [end:harvest]

    /**
     * The nearest tile with crystal, counted in steps from {@code from}, that no other harvester of the side is
     * going to; one that another is going to if there is no other.
     */
    private Cell findCrystal(Cell from, Unit harvester) {
        boolean[] seen = new boolean[width * height];
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        queue.add(from);
        seen[from.row() * width + from.col()] = true;
        Cell shared = null;
        while (!queue.isEmpty()) {
            Cell cell = queue.poll();
            if (crystal[cell.row() * width + cell.col()] > 0) {
                if (!taken(cell, harvester)) return cell;
                if (shared == null) shared = cell;
            }
            for (int d = 0; d < 8; d++) {
                int c = cell.col() + DX[d];
                int r = cell.row() + DY[d];
                if (!contains(c, r) || seen[r * width + c] || !open(c, r)) continue;
                if (Math.max(Math.abs(c - from.col()), Math.abs(r - from.row())) > Field.CRYSTAL_SEARCH) continue;
                seen[r * width + c] = true;
                queue.add(new Cell(c, r));
            }
        }
        return shared;
    }

    private boolean taken(Cell cell, Unit harvester) {
        for (Unit unit : units) {
            if (unit != harvester && unit.side() == harvester.side() && unit.task == Task.HARVEST
                    && cell.equals(unit.goal)) {
                return true;
            }
        }
        return false;
    }

    /** A full harvester drives to the nearest refinery of its side and onto its pad, when the pad is free. */
    private void bringHome(Unit unit, double seconds) {
        Building refinery = null;
        double nearest = Double.MAX_VALUE;
        for (Building building : buildings) {
            if (building.side() != unit.side() || building.type().dock() < 0) continue;
            double d = Math.hypot(building.dockX() - unit.x, building.dockY() - unit.y)
                    + (building.docked != 0 ? 4 * Field.TILE : 0);
            if (d < nearest) {
                refinery = building;
                nearest = d;
            }
        }
        if (refinery == null) {
            unit.goal = null;
            return;
        }
        Cell pad = new Cell(refinery.col() + refinery.type().dock(), refinery.row() + refinery.type().height() - 1);
        double d = Math.hypot(refinery.dockX() - unit.x, refinery.dockY() - unit.y);
        if (d < 1.4 * Field.TILE && refinery.docked == 0) {
            refinery.docked = unit.id();
            unit.task = Task.UNLOAD;
            unit.goal = null;
            return;
        }
        if (refinery.docked != 0 && d < 3 * Field.TILE) return;     // waits its turn beside the pad
        drive(unit, pad, seconds);
    }

    // --8<-- [start:unload]
    /** On the pad, the crystal goes into the refinery; what the storage cannot hold is lost. */
    private void unload(Unit unit, double seconds) {
        Building refinery = null;
        for (Building building : buildings) if (building.docked == unit.id()) refinery = building;
        if (refinery == null) {
            unit.task = Task.RETURN;
            return;
        }
        double dx = refinery.dockX() - unit.x;
        double dy = refinery.dockY() - unit.y;
        double d = Math.hypot(dx, dy);
        if (d > 0.5) {
            double step = Math.min(d, unit.type().speed() * Field.TILE * seconds);
            unit.x += dx / d * step;
            unit.y += dy / d * step;
            unit.angle = Angles.turnTowards(unit.angle, Math.PI / 2, Field.TURN * seconds);
            return;
        }
        unit.work += Field.UNLOAD_RATE * seconds;
        int give = Math.min((int) unit.work, unit.load);
        if (give > 0) {
            unit.work -= give;
            unit.load -= give;
            House house = houses.get(unit.side());
            int lost = house.credits.earn(give);
            house.harvested += give - lost;
            if (lost > 0 && time - house.full > Field.ALARM_SECONDS) {
                house.full = time;
                events.add(new Event.StorageFull(unit.side()));
            }
            if (unit.load == 0) events.add(new Event.Unloaded(unit, unit.type().capacity()));
        }
        if (unit.load == 0) {
            refinery.docked = 0;
            unit.work = 0;
            unit.task = Task.HARVEST;
            unit.goal = null;
        }
    }
    // --8<-- [end:unload]

    // --8<-- [start:defend]
    /** A turret turns towards the nearest enemy vehicle in range and fires; slower on low power. */
    private void defend(Building building, double seconds) {
        Weapon weapon = building.type().weapon();
        if (weapon == null) return;
        boolean low = houses.get(building.side()).lowPower();
        building.reload -= seconds * (low ? Field.LOW_POWER : 1);
        building.sinceShot += seconds;
        Unit enemy = null;
        double nearest = weapon.range() * Field.TILE;
        for (Unit unit : units) {
            if (unit.side() == building.side() || !unit.alive()) continue;
            double d = gap(building.x(), building.y(), unit);
            if (d <= nearest && visible(building.side(), unit)) {
                enemy = unit;
                nearest = d;
            }
        }
        if (enemy == null) return;
        double want = Math.atan2(enemy.y - building.y(), enemy.x - building.x());
        building.turret = Angles.turnTowards(building.turret, want, Field.TURRET_TURN * seconds);
        if (Math.abs(Angles.difference(building.turret, want)) > Field.AIMED || building.reload > 0) return;
        building.reload = weapon.reload();
        building.sinceShot = 0;
        fire(weapon, building.side(), building.id(), building.x() + Math.cos(building.turret) * 9,
                building.y() + Math.sin(building.turret) * 9, enemy);
    }
    // --8<-- [end:defend]

    // --8<-- [start:shots]
    /** Shells follow their target and hit it; rockets fly to their point and burst there. */
    private void fly(double seconds) {
        for (Iterator<Shot> it = shots.iterator(); it.hasNext(); ) {
            Shot shot = it.next();
            Thing target = shot.target != 0 ? things.get(shot.target) : null;
            if (target != null && target.alive()) {
                shot.toX = target.x();
                shot.toY = target.y();
            }
            shot.left -= seconds;
            double share = shot.left <= 0 ? 1 : seconds / (shot.left + seconds);
            shot.x += (shot.toX - shot.x) * share;
            shot.y += (shot.toY - shot.y) * share;
            shot.angle = Math.atan2(shot.toY - shot.lastY, shot.toX - shot.lastX);
            if (shot.left > 0) continue;
            it.remove();
            if (shot.weapon.kind() == Weapon.Kind.GUN) {
                if (target != null && target.alive()) hurt(target, shot.weapon.damage(), shot.side, shot.shooter);
                events.add(new Event.Hit(shot.x, shot.y));
            } else {
                burst(shot);
            }
        }
    }

    /** A rocket's burst hurts every enemy it reaches, those at its edge half as much. */
    private void burst(Shot shot) {
        double reach = shot.weapon.splash() * Field.TILE;
        List<Thing> hit = new ArrayList<>();
        for (Unit unit : units) {
            if (unit.side() != shot.side && unit.alive() && gap(shot.x, shot.y, unit) <= reach) hit.add(unit);
        }
        for (Building building : buildings) {
            if (building.side() != shot.side && gap(shot.x, shot.y, building) <= reach) hit.add(building);
        }
        for (Thing thing : hit) {
            double share = 1 - 0.5 * gap(shot.x, shot.y, thing) / reach;
            hurt(thing, shot.weapon.damage() * share, shot.side, shot.shooter);
        }
        events.add(new Event.Burst(shot.x, shot.y, reach));
    }
    // --8<-- [end:shots]

    // --8<-- [start:hurt]
    /** Damage, less the armour. The side hit is told, now and then, and an idle vehicle fires back. */
    private void hurt(Thing thing, double damage, Side by, int shooter) {
        switch (thing) {
            case Unit unit -> {
                unit.health -= damage * (1 - unit.type().armour());
                Thing attacker = things.get(shooter);
                if (unit.task == Task.IDLE && unit.type().armed() && attacker instanceof Unit) {
                    unit.task = Task.ATTACK;
                    unit.target = shooter;
                }
            }
            case Building building -> building.health -= damage * (1 - building.type().armour());
        }
        House house = houses.get(thing.side());
        house.attacker = shooter;
        house.attackedX = thing.x();
        house.attackedY = thing.y();
        if (time - house.alarm > Field.ALARM_SECONDS) {
            house.alarm = time;
            events.add(new Event.UnderAttack(thing.side(), thing.x(), thing.y()));
        }
    }
    // --8<-- [end:hurt]

    /** The airlifters fly in, set their harvester down at the refinery, and fly back off the map. */
    private void carry(double seconds) {
        for (Iterator<Airlifter> it = airlifters.iterator(); it.hasNext(); ) {
            Airlifter airlifter = it.next();
            double dx = airlifter.toX - airlifter.x;
            double dy = airlifter.toY - airlifter.y;
            double d = Math.hypot(dx, dy);
            double step = Field.AIRLIFTER_SPEED * seconds;
            airlifter.angle = Math.atan2(dy, dx);
            if (d > step) {
                airlifter.x += dx / d * step;
                airlifter.y += dy / d * step;
                continue;
            }
            airlifter.x = airlifter.toX;
            airlifter.y = airlifter.toY;
            if (airlifter.cargo == null) {
                it.remove();
                continue;
            }
            Cell drop = cellOf(airlifter.x, airlifter.y);
            List<Cell> slots = Formation.slots(drop, 1, width, height, this::open);
            if (!slots.isEmpty()) {
                Cell slot = slots.getFirst();
                Unit unit = add(airlifter.cargo, airlifter.side, (slot.col() + 0.5) * Field.TILE,
                        (slot.row() + 0.5) * Field.TILE, airlifter.angle);
                if (unit.type().harvests()) unit.task = Task.HARVEST;
                events.add(new Event.Deployed(unit));
            }
            airlifter.cargo = null;
            airlifter.toX = airlifter.homeX;
            airlifter.toY = airlifter.homeY;
        }
    }

    /** A new refinery's harvester comes by airlifter, from the edge of the map nearest to it. */
    private void fly(Building refinery) {
        double x = refinery.dockX();
        double y = refinery.dockY();
        double w = width * Field.TILE;
        double h = height * Field.TILE;
        double out = 3 * Field.TILE;
        double[][] edges = {{-out, y, x}, {w + out, y, w - x}, {x, -out, y}, {x, h + out, h - y}};
        double[] from = edges[0];
        for (double[] edge : edges) if (edge[2] < from[2]) from = edge;
        airlifters.add(new Airlifter(refinery.side(), from[0], from[1], x, y, rules.harvester()));
    }

    // --8<-- [start:burrower]
    /**
     * The burrower hunts the nearest vehicle on the sand, harvesters above all, for they shake the ground; with
     * none in reach it wanders. It turns away from rock. A vehicle in its mouth is gone; then it dives, and
     * comes up again far away.
     */
    private void burrower(double seconds) {
        if (burrower == null) {
            if (time < Field.BURROWER_FIRST) return;
            Cell cell = sandFarFromAll();
            if (cell == null) return;
            burrower = new Burrower((cell.col() + 0.5) * Field.TILE, (cell.row() + 0.5) * Field.TILE);
            wander();
            events.add(new Event.BurrowerRose(burrower.x, burrower.y));
            return;
        }
        if (!burrower.up) {
            burrower.timer -= seconds;
            if (burrower.timer > 0) return;
            Cell cell = sandFarFromAll();
            if (cell == null) {
                burrower.timer = 5;
                return;
            }
            burrower.x = burrower.lastX = (cell.col() + 0.5) * Field.TILE;
            burrower.y = burrower.lastY = (cell.row() + 0.5) * Field.TILE;
            burrower.up = true;
            wander();
            events.add(new Event.BurrowerRose(burrower.x, burrower.y));
            return;
        }
        burrower.sniff -= seconds;
        if (burrower.sniff <= 0) {
            burrower.sniff = 0.5;
            burrower.prey = 0;
            double best = Field.BURROWER_SMELL * Field.TILE;
            for (Unit unit : units) {
                if (!onSand(unit.x, unit.y)) continue;
                double d = Math.hypot(unit.x - burrower.x, unit.y - burrower.y) / (unit.type().harvests() ? 2 : 1);
                if (d < best) {
                    best = d;
                    burrower.prey = unit.id();
                }
            }
        }
        Thing prey = things.get(burrower.prey);
        if (prey instanceof Unit unit && unit.alive() && onSand(unit.x, unit.y)) {
            burrower.toX = unit.x;
            burrower.toY = unit.y;
            if (Math.hypot(unit.x - burrower.x, unit.y - burrower.y) < Field.BURROWER_BITE) {
                unit.health = 0;
                events.add(new Event.BurrowerAte(unit));
                burrower.meals++;
                burrower.up = false;
                burrower.timer = Field.BURROWER_DIVE;
                burrower.prey = 0;
                return;
            }
        } else if (Math.hypot(burrower.toX - burrower.x, burrower.toY - burrower.y) < Field.TILE) {
            wander();
        }
        double want = Math.atan2(burrower.toY - burrower.y, burrower.toX - burrower.x);
        burrower.angle = Angles.turnTowards(burrower.angle, want, 2.5 * seconds);
        double nx = burrower.x + Math.cos(burrower.angle) * Field.BURROWER_SPEED * seconds;
        double ny = burrower.y + Math.sin(burrower.angle) * Field.BURROWER_SPEED * seconds;
        if (onSand(nx, ny)) {
            burrower.x = nx;
            burrower.y = ny;
        } else {
            burrower.prey = 0;
            burrower.sniff = 2;
            wander();
        }
    }
    // --8<-- [end:burrower]

    private boolean onSand(double x, double y) {
        int col = (int) Math.floor(x / Field.TILE);
        int row = (int) Math.floor(y / Field.TILE);
        return contains(col, row) && level.ground(col, row).sandy();
    }

    /** The burrower picks a sand tile within ten tiles to wander to. */
    private void wander() {
        for (int i = 0; i < 40; i++) {
            int col = (int) Math.floor(burrower.x / Field.TILE) + random.nextInt(-10, 11);
            int row = (int) Math.floor(burrower.y / Field.TILE) + random.nextInt(-10, 11);
            if (contains(col, row) && level.ground(col, row).sandy()) {
                burrower.toX = (col + 0.5) * Field.TILE;
                burrower.toY = (row + 0.5) * Field.TILE;
                return;
            }
        }
        burrower.toX = burrower.x;
        burrower.toY = burrower.y;
    }

    /** A sand tile at least twelve tiles from every vehicle and building, or {@code null}. */
    private Cell sandFarFromAll() {
        double far = 12 * Field.TILE;
        for (int i = 0; i < 200; i++) {
            int col = random.nextInt(width);
            int row = random.nextInt(height);
            if (!level.ground(col, row).sandy()) continue;
            double x = (col + 0.5) * Field.TILE;
            double y = (row + 0.5) * Field.TILE;
            boolean alone = true;
            for (Unit unit : units) if (Math.hypot(unit.x - x, unit.y - y) < far) alone = false;
            for (Building building : buildings) if (building.distance(x, y) < far) alone = false;
            if (alone) return new Cell(col, row);
        }
        return null;
    }

    /** Takes away what was destroyed, and counts it. */
    private void bury() {
        boolean changed = false;
        for (Iterator<Unit> it = units.iterator(); it.hasNext(); ) {
            Unit unit = it.next();
            if (unit.alive()) continue;
            it.remove();
            things.remove(unit.id());
            for (Building building : buildings) if (building.docked == unit.id()) building.docked = 0;
            houses.get(unit.side()).lost++;
            houses.get(unit.side().enemy()).destroyed++;
            events.add(new Event.Destroyed(unit));
        }
        for (Iterator<Building> it = buildings.iterator(); it.hasNext(); ) {
            Building building = it.next();
            if (building.alive()) continue;
            it.remove();
            clear(building);
            houses.get(building.side().enemy()).destroyed++;
            events.add(new Event.Collapsed(building));
            changed = true;
        }
        if (changed) account();
    }

    // --8<-- [start:look]
    /** Each side's fog: what its vehicles and buildings see now, and the enemy buildings it has found or lost. */
    void look() {
        for (House house : houses.values()) {
            FogOfWar fog = house.fog;
            fog.begin();
            for (Unit unit : units) {
                if (unit.side() != house.side()) continue;
                fog.look((int) Math.floor(unit.x / Field.TILE), (int) Math.floor(unit.y / Field.TILE),
                        (int) Math.ceil(unit.type().sight()));
            }
            for (Building building : buildings) {
                if (building.side() != house.side()) continue;
                BuildingType type = building.type();
                fog.look(building.col() + type.width() / 2, building.row() + type.height() / 2,
                        (int) Math.ceil(type.sight() + Math.max(type.width(), type.height()) / 2.0));
            }
            fog.end();
            for (Building building : buildings) {
                if (building.side() != house.side() && visible(house.side(), building)) {
                    house.known.put(building.id(), new Ghost(building.id(), building.type(), building.side(),
                            building.col(), building.row()));
                }
            }
            house.known.values().removeIf(ghost -> !things.containsKey(ghost.id()) && seesAny(fog, ghost));
        }
    }
    // --8<-- [end:look]

    private static boolean seesAny(FogOfWar fog, Ghost ghost) {
        for (int dy = 0; dy < ghost.type().height(); dy++) {
            for (int dx = 0; dx < ghost.type().width(); dx++) {
                if (fog.visible(ghost.col() + dx, ghost.row() + dy)) return true;
            }
        }
        return false;
    }

    /** A side without buildings has lost. */
    private void judge() {
        if (winner != null) return;
        for (Side side : Side.values()) {
            boolean any = false;
            for (Building building : buildings) if (building.side() == side) any = true;
            if (any) continue;
            winner = side.enemy();
            phase = winner == Side.BLUE ? Phase.WON : Phase.LOST;
            events.add(new Event.Over(winner));
            return;
        }
    }

    // ---- bookkeeping ----------------------------------------------------------------------------

    /** Power made and used, and the storage, of each side, from its buildings. */
    void account() {
        for (House house : houses.values()) {
            int made = 0;
            int used = 0;
            int storage = 0;
            for (Building building : buildings) {
                if (building.side() != house.side()) continue;
                int power = building.type().power();
                if (power > 0) made += power;
                else used -= power;
                storage += building.type().storage();
            }
            house.powerMade = made;
            house.powerUsed = used;
            house.credits.setCapacity(storage);
        }
    }

    Unit add(UnitType type, Side side, double x, double y, double angle) {
        return add(new Unit(nextId++, type, side, x, y, angle));
    }

    Unit add(Unit unit) {
        units.add(unit);
        things.put(unit.id(), unit);
        return unit;
    }

    Building add(BuildingType type, Side side, int col, int row) {
        return add(new Building(nextId++, type, side, col, row, time));
    }

    Building add(Building building) {
        buildings.add(building);
        things.put(building.id(), building);
        BuildingType type = building.type();
        for (int dy = 0; dy < type.height(); dy++) {
            for (int dx = 0; dx < type.width(); dx++) {
                int i = (building.row() + dy) * width + building.col() + dx;
                occupant[i] = building.id();
                wall[i] = type.solid(dx, dy);
            }
        }
        changed();
        return building;
    }

    private void remove(Building building) {
        buildings.remove(building);
        clear(building);
    }

    private void clear(Building building) {
        things.remove(building.id());
        BuildingType type = building.type();
        for (int dy = 0; dy < type.height(); dy++) {
            for (int dx = 0; dx < type.width(); dx++) {
                int i = (building.row() + dy) * width + building.col() + dx;
                occupant[i] = 0;
                wall[i] = false;
            }
        }
        changed();
    }

    /** A building went up or came down: every flow field may be wrong now. */
    private void changed() {
        version++;
        fields.invalidate();
        approaches.clear();
    }

    static Cell cellOf(double x, double y) {
        return new Cell((int) Math.floor(x / Field.TILE), (int) Math.floor(y / Field.TILE));
    }

    /** How many flow fields were searched so far: for the tests of the cache. */
    int searches() {
        return fields.searches();
    }

    /** Puts crystal back as a save file kept it. */
    void restoreCrystal(int[] amounts) {
        System.arraycopy(amounts, 0, crystal, 0, crystal.length);
        crystalVersion++;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    /** The production queues of a side, for the save file. */
    Production<BuildingType> yard(Side side) {
        return houses.get(side).yard;
    }
}
