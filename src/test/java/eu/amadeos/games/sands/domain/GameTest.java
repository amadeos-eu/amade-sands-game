package eu.amadeos.games.sands.domain;

import eu.amadeos.gameengine.tile.Cell;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTest {
    private static final double STEP = 1.0 / 120;
    private static final Rules RULES = Rules.load();

    /** Mission {@code number} with the player's side left to the test. */
    private static Game mission(int number) {
        return new Game(Level.load(number, RULES), RULES, 1, Set.of());
    }

    private static List<Event> run(Game game, double seconds) {
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < Math.round(seconds / STEP); i++) {
            game.update(STEP);
            events.addAll(game.events());
        }
        return events;
    }

    private static Unit vehicle(Game game, String kind, Side side, int col, int row) {
        return game.add(RULES.unit(kind), side, (col + 0.5) * Field.TILE, (row + 0.5) * Field.TILE, 0);
    }

    private static List<Unit> of(Game game, Side side, String kind) {
        return game.units().stream().filter(u -> u.side() == side && u.type().id().equals(kind)).toList();
    }

    private static Building first(Game game, Side side, String kind) {
        return game.buildings().stream().filter(b -> b.side() == side && b.type().id().equals(kind)).findFirst()
                .orElseThrow();
    }

    /** The open sand tile nearest to a tile, for tests that need one. */
    private static Cell sand(Game game, int col, int row) {
        for (int r = 0; r < 64; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int c = col + dx;
                    int w = row + dy;
                    if (game.contains(c, w) && game.level().ground(c, w) == Ground.SAND && game.open(c, w)) {
                        return new Cell(c, w);
                    }
                }
            }
        }
        throw new AssertionError("no sand");
    }

    // --8<-- [start:test-group]
    @Test
    void aGroupSentToOneTileSpreadsOverSlotsOfItsOwn() {
        Game game = mission(1);
        for (Unit unit : game.units()) unit.health = 0;
        run(game, STEP);
        int searches = game.searches();
        List<Unit> group = new ArrayList<>();
        for (int i = 0; i < 6; i++) group.add(vehicle(game, "tank", Side.BLUE, 14 + i % 3, 15 + i / 3));
        Cell target = sand(game, 30, 26);
        game.move(group, target);
        Set<Cell> slots = new HashSet<>();
        for (Unit tank : group) slots.add(tank.goal());
        assertEquals(6, slots.size(), "a slot each");
        run(game, 40);
        Set<Cell> arrived = new HashSet<>();
        for (Unit tank : group) {
            assertEquals(Task.IDLE, tank.task(), tank + " has arrived");
            assertTrue(Math.hypot(tank.x() - (tank.guard.col() + 0.5) * Field.TILE,
                    tank.y() - (tank.guard.row() + 0.5) * Field.TILE) < Field.TILE, tank + " stands at its slot");
            arrived.add(tank.cell());
            assertTrue(Math.abs(tank.cell().col() - target.col()) <= 3 && Math.abs(tank.cell().row() - target.row()) <= 3);
        }
        assertTrue(arrived.size() >= 5, "they stand apart: " + arrived);
        assertEquals(1, game.searches() - searches, "the group shares one flow field");
    }
    // --8<-- [end:test-group]

    // --8<-- [start:test-harvest]
    @Test
    void aHarvesterFillsUpOnTheSpiceAndBringsItHome() {
        Game game = mission(1);
        Unit harvester = of(game, Side.BLUE, "harvester").getFirst();
        House house = game.house(Side.BLUE);
        house.credits.pay(1000);
        int credits = house.credits();
        int spice = 0;
        for (int row = 0; row < 64; row++) for (int col = 0; col < 64; col++) spice += game.spice(col, row);
        List<Event> events = run(game, 90);
        Event.Unloaded unloaded = (Event.Unloaded) events.stream().filter(e -> e instanceof Event.Unloaded)
                .findFirst().orElseThrow();
        assertEquals(harvester, unloaded.harvester());
        assertEquals(credits + 700, house.credits(), "a full load, 700 credits");
        int left = 0;
        for (int row = 0; row < 64; row++) for (int col = 0; col < 64; col++) left += game.spice(col, row);
        int taken = 0;
        for (Side side : Side.values()) taken += game.house(side).harvested();
        for (Unit unit : game.units()) taken += unit.load();
        assertEquals(spice - taken, left, "taken from the ground, none made up");
    }
    // --8<-- [end:test-harvest]

    @Test
    void whatTheStorageCannotHoldIsLost() {
        Game game = mission(1);
        House house = game.house(Side.BLUE);
        house.credits.refund(house.storage() - house.credits() - 100);
        List<Event> events = run(game, 90);
        assertTrue(events.stream().anyMatch(e -> e instanceof Event.StorageFull full && full.side() == Side.BLUE));
        assertEquals(house.storage(), house.credits(), "full to the brim");
    }

    // --8<-- [start:test-produce]
    @Test
    void theFactoryMakesWhatItIsPaidForAndLowPowerHalvesItsSpeed() {
        Game game = mission(1);
        assertFalse(game.produce(Side.BLUE, RULES.unit("tank")), "no factory yet");
        game.add(RULES.building("factory"), Side.BLUE, 10, 9);
        game.account();
        assertTrue(game.produce(Side.BLUE, RULES.unit("tank")));
        assertFalse(game.produce(Side.BLUE, RULES.unit("launcher")), "a launcher needs an outpost");
        int credits = game.house(Side.BLUE).credits();
        List<Event> events = run(game, 13.9);
        assertTrue(events.stream().noneMatch(e -> e instanceof Event.Deployed));
        events = run(game, 0.2);
        Event.Deployed deployed = (Event.Deployed) events.stream().filter(e -> e instanceof Event.Deployed)
                .findFirst().orElseThrow();
        assertEquals("tank", deployed.unit().type().id());
        assertEquals(12, deployed.unit().cell().row(), "below the factory's door");
        assertTrue(game.house(Side.BLUE).credits() <= credits - 300 + 700, "paid, whatever the harvester brought");

        // Four more factories use more power than the windtrap makes: half speed.
        for (int i = 0; i < 4; i++) game.add(RULES.building("factory"), Side.BLUE, 20 + 5 * i, 40);
        game.account();
        assertTrue(game.house(Side.BLUE).lowPower());
        game.produce(Side.BLUE, RULES.unit("trike"));
        run(game, 8.1);
        assertEquals(0.5, game.house(Side.BLUE).factory().progress(), 0.02, "half of the eight seconds' work");
    }
    // --8<-- [end:test-produce]

    // --8<-- [start:test-place]
    @Test
    void buildingsStandOnRockNearTheBaseAndNowhereElse() {
        Game game = mission(1);
        BuildingType silo = RULES.building("silo");
        House house = game.house(Side.BLUE);
        assertEquals(Game.Placement.NOT_READY, game.place(Side.BLUE, 10, 2));
        house.yard.keep(silo);
        assertEquals(Game.Placement.OK, game.placement(Side.BLUE, silo, 10, 2), "on rock beside the windtrap");
        assertEquals(Game.Placement.TOO_FAR, game.placement(Side.BLUE, silo, 14, 2), "rock, but too far away");
        assertEquals(Game.Placement.OCCUPIED, game.placement(Side.BLUE, silo, 4, 4), "on the yard");
        Cell sand = sand(game, 20, 6);
        assertEquals(Game.Placement.NOT_ROCK, game.placement(Side.BLUE, silo, sand.col(), sand.row()));
        assertEquals(Game.Placement.OUTSIDE, game.placement(Side.BLUE, silo, -1, 5));
        Unit tank = of(game, Side.BLUE, "tank").getFirst();
        Cell under = tank.cell();
        assertEquals(Game.Placement.OCCUPIED, game.placement(Side.BLUE, silo, under.col(), under.row()), "a tank is there");
        assertEquals(Game.Placement.OK, game.place(Side.BLUE, 10, 2));
        assertEquals(2000 + 1500, house.storage(), "the yard and the refinery store 2,000, the silo 1,500 more");
        assertNull(house.yard().ready());
    }
    // --8<-- [end:test-place]

    @Test
    void aNewRefineryComesWithAHarvesterByCarryall() {
        Game game = mission(1);
        game.house(Side.BLUE).yard.keep(RULES.building("refinery"));
        assertEquals(Game.Placement.OK, game.place(Side.BLUE, 10, 2));
        assertEquals(1, game.carryalls().size());
        int before = of(game, Side.BLUE, "harvester").size();
        List<Event> events = run(game, 20);
        assertTrue(events.stream().anyMatch(e -> e instanceof Event.Deployed d && d.unit().type().harvests()));
        assertEquals(before + 1, of(game, Side.BLUE, "harvester").size());
        assertTrue(game.carryalls().isEmpty(), "and flew off again");
    }

    // --8<-- [start:test-fight]
    @Test
    void aTurretDestroysATrikeThatComesTooClose() {
        Game game = mission(1);
        game.add(RULES.building("turret"), Side.BLUE, 17, 10);
        game.account();
        Unit trike = vehicle(game, "trike", Side.RED, 24, 11);
        game.move(List.of(trike), new Cell(19, 11));
        List<Event> events = run(game, 15);
        assertFalse(trike.alive());
        assertTrue(events.stream().anyMatch(e -> e instanceof Event.Destroyed d && d.unit() == trike));
        assertTrue(events.stream().anyMatch(e -> e instanceof Event.UnderAttack a && a.side() == Side.RED));
        assertEquals(1, game.house(Side.BLUE).destroyed());
    }
    // --8<-- [end:test-fight]

    @Test
    void rocketsBurstAndHurtAllAround() {
        Game game = mission(1);
        Unit launcher = vehicle(game, "launcher", Side.BLUE, 30, 30);
        List<Unit> trikes = new ArrayList<>();
        for (int i = 0; i < 3; i++) trikes.add(vehicle(game, "trike", Side.RED, 34, 30 + i));
        game.stop(trikes);
        game.attack(List.of(launcher), trikes.get(1));
        List<Event> events = run(game, 6);
        assertTrue(events.stream().anyMatch(e -> e instanceof Event.Burst));
        long hurt = trikes.stream().filter(t -> t.health() < t.maxHealth()).count();
        assertTrue(hurt >= 2, "the burst reaches more than one: " + hurt);
    }

    // --8<-- [start:test-ghosts]
    @Test
    void anEnemyBuildingOnceSeenStaysOnTheMapUntilItIsSeenGone() {
        Game game = mission(1);
        Building windtrap = first(game, Side.RED, "windtrap");
        assertFalse(game.house(Side.BLUE).known().stream().anyMatch(g -> g.id() == windtrap.id()), "far off, unseen");
        Unit scout = vehicle(game, "trike", Side.BLUE, windtrap.col() - 4, windtrap.row() + 1);
        run(game, 0.1);
        assertTrue(game.house(Side.BLUE).known().stream().anyMatch(g -> g.id() == windtrap.id()), "found");
        scout.health = 0;
        run(game, 0.1);
        windtrap.health = 0;                              // destroyed while nobody of blue looks
        run(game, 0.1);
        assertTrue(game.house(Side.BLUE).known().stream().anyMatch(g -> g.id() == windtrap.id()), "still a ghost");
        vehicle(game, "trike", Side.BLUE, windtrap.col() - 4, windtrap.row() + 1);
        run(game, 0.1);
        assertFalse(game.house(Side.BLUE).known().stream().anyMatch(g -> g.id() == windtrap.id()), "seen gone");
    }
    // --8<-- [end:test-ghosts]

    // --8<-- [start:test-worm]
    @Test
    void theWormSwallowsVehiclesOnTheSandButNotOnRock() {
        Game game = mission(2);
        game.time = Field.WORM_FIRST - 1;
        Cell open = sand(game, 24, 30);
        Unit onSand = vehicle(game, "trike", Side.BLUE, open.col(), open.row());
        game.stop(List.of(onSand));
        run(game, 1.1);
        assertNotNull(game.worm());
        game.worm.x = onSand.x() + 40;
        game.worm.y = onSand.y();
        Unit onRock = vehicle(game, "trike", Side.BLUE, 12, 34);
        List<Event> events = run(game, 10);
        assertTrue(events.stream().anyMatch(e -> e instanceof Event.WormAte ate && ate.unit() == onSand));
        assertFalse(onSand.alive());
        assertTrue(onRock.alive());
        assertFalse(game.worm().up(), "it dives after a meal");
    }
    // --8<-- [end:test-worm]

    @Test
    void aSideWithoutBuildingsHasLost() {
        Game game = mission(1);
        for (Building building : game.buildings()) if (building.side() == Side.RED) building.health = 0;
        List<Event> events = run(game, 0.1);
        assertEquals(Game.Phase.WON, game.phase());
        assertEquals(Side.BLUE, game.winner());
        assertEquals(1, events.stream().filter(e -> e instanceof Event.Over).count());
    }

    // --8<-- [start:test-computer]
    @Test
    void theComputerBuildsItsBaseAndAttacks() {
        Game game = new Game(Level.load(1, RULES), RULES, 3, Set.of(Side.RED));
        List<Event> events = run(game, 480);
        long placed = events.stream().filter(e -> e instanceof Event.Placed p && p.building().side() == Side.RED).count();
        assertTrue(placed >= 5, "it built " + placed);
        assertTrue(game.commander(Side.RED).wave() >= 1, "and attacked");
        assertTrue(game.house(Side.RED).harvested() > 2000, "with spice it harvested");
        assertTrue(game.units().stream().anyMatch(u -> u.side() == Side.RED && u.task() == Task.HUNT)
                || game.buildings().stream().anyMatch(b -> b.side() == Side.BLUE && b.health() < b.maxHealth()),
                "its army is out hunting, or has found the base");
    }
    // --8<-- [end:test-computer]

    // --8<-- [start:test-save]
    @Test
    void aBattleSavedAndLoadedGoesOnExactlyAsItWouldHave() {
        Game game = new Game(Level.load(2, RULES), RULES, 11, EnumSet.allOf(Side.class));
        run(game, 300);
        Map<String, String> saved = Archive.write(game);
        Game loaded = Archive.read(saved, RULES, number -> Level.load(number, RULES));
        assertEquals(saved, Archive.write(loaded), "nothing lost on the way");
        run(game, 120);
        run(loaded, 120);
        assertEquals(Archive.write(game), Archive.write(loaded), "and both went on alike");
        assertTrue(game.units().size() > 10, "with a battle going on: " + game.units().size());
    }
    // --8<-- [end:test-save]
}
