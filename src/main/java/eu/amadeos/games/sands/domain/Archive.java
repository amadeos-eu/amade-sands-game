package eu.amadeos.games.sands.domain;

import eu.amadeos.gameengine.economy.Production;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.save.Snapshot;
import eu.amadeos.gameengine.tile.Cell;

import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A whole battle as the values of a save file, and back: every vehicle with its task and its load, every
 * building, the shells and rockets in flight, the spice left, the worm, both houses with their credits,
 * queues and what they have seen, the commanders' plans, and the random generator where it stood. The map
 * itself is not saved, only its mission's number: the map comes from the game, as at the start.
 *
 * <p>Loaded, the battle goes on exactly as it would have: {@code GameTest} plays a battle on, saves it,
 * loads it, plays both on and finds them alike to the last bit.
 */
public final class Archive {
    /** Changes when the values change their meaning; an older file is not read. */
    public static final int VERSION = 1;

    private Archive() {
    }

    // --8<-- [start:write]
    public static Map<String, String> write(Game game) {
        Snapshot.Writer w = Snapshot.writer();
        w.put("mission", game.level().number());
        w.put("time", game.time).put("thought", game.thought).put("next", game.nextId);
        w.put("phase", game.phase).put("winner", game.winner == null ? "none" : game.winner.id());
        w.put("seed", game.random.seed()).put("random", game.random.state());
        w.put("spice", spice(game.spice));
        for (House house : game.houses.values()) house(w.child("house." + house.side().id()), house);
        for (Commander commander : game.commanders.values()) {
            w.add("commander").put("side", commander.side().id()).put("attack", commander.nextAttack)
                    .put("wave", commander.wave).put("mixed", commander.mixed).put("failures", commander.failures);
        }
        w.list("building");
        for (Building b : game.buildings) {
            w.add("building").put("id", b.id()).put("kind", b.type().id()).put("side", b.side().id())
                    .put("col", b.col()).put("row", b.row()).put("health", b.health).put("turret", b.turret)
                    .put("reload", b.reload).put("since", b.sinceShot).put("docked", b.docked).put("built", b.built);
        }
        w.list("unit");
        for (Unit u : game.units) {
            Snapshot.Writer unit = w.add("unit").put("id", u.id()).put("kind", u.type().id())
                    .put("side", u.side().id()).put("x", u.x).put("y", u.y).put("angle", u.angle)
                    .put("turret", u.turret).put("health", u.health).put("task", u.task).put("target", u.target)
                    .put("reload", u.reload).put("since", u.sinceShot).put("load", u.load).put("work", u.work)
                    .put("stuck", u.stuck).put("moving", u.moving).put("harvesting", u.harvesting);
            cell(unit, "goal", u.goal);
            cell(unit, "way", u.way);
            cell(unit, "guard", u.guard);
            cell(unit, "field", u.field);
        }
        w.list("shot");
        for (Shot s : game.shots) {
            w.add("shot").put("weapon", owner(game.rules(), s.weapon)).put("side", s.side.id())
                    .put("shooter", s.shooter).put("target", s.target).put("x", s.x).put("y", s.y)
                    .put("toX", s.toX).put("toY", s.toY).put("angle", s.angle).put("left", s.left)
                    .put("flight", s.flight);
        }
        w.list("carryall");
        for (Carryall c : game.carryalls) {
            w.add("carryall").put("side", c.side.id()).put("x", c.x).put("y", c.y).put("angle", c.angle)
                    .put("toX", c.toX).put("toY", c.toY).put("homeX", c.homeX).put("homeY", c.homeY)
                    .put("cargo", c.cargo == null ? "none" : c.cargo.id());
        }
        if (game.worm != null) {
            Worm m = game.worm;
            w.child("worm").put("x", m.x).put("y", m.y).put("angle", m.angle).put("up", m.up).put("timer", m.timer)
                    .put("prey", m.prey).put("toX", m.toX).put("toY", m.toY).put("sniff", m.sniff)
                    .put("meals", m.meals);
        }
        return w.values();
    }
    // --8<-- [end:write]

    private static void house(Snapshot.Writer w, House h) {
        w.put("credits", h.credits.amount()).put("seen", h.fog.seenAsText()).put("alarm", h.alarm)
                .put("attacker", h.attacker).put("attackedX", h.attackedX).put("attackedY", h.attackedY)
                .put("full", h.full).put("harvested", h.harvested).put("made", h.made).put("lost", h.lost)
                .put("destroyed", h.destroyed);
        queue(w.child("yard"), h.yard);
        queue(w.child("factory"), h.factory);
        w.list("known");
        for (Ghost ghost : h.known.values()) {
            w.add("known").put("id", ghost.id()).put("kind", ghost.type().id()).put("side", ghost.side().id())
                    .put("col", ghost.col()).put("row", ghost.row());
        }
    }

    // --8<-- [start:queue]
    /** A production queue: its orders by name, how far the first one is, and what waits to be placed. */
    private static <T> void queue(Snapshot.Writer w, Production<T> queue) {
        w.put("paid", queue.paid()).put("done", queue.progress()).put("held", queue.held());
        w.put("ready", queue.ready() == null ? "none" : name(queue.ready()));
        w.list("order");
        for (Production.Order<T> order : queue.queue()) w.add("order").put("item", name(order.item()));
    }
    // --8<-- [end:queue]

    private static String name(Object item) {
        return item instanceof UnitType unit ? unit.id() : ((BuildingType) item).id();
    }

    private static void cell(Snapshot.Writer w, String key, Cell cell) {
        if (cell != null) w.child(key).put("col", cell.col()).put("row", cell.row());
    }

    /** A weapon by the vehicle or building that has it: {@code unit tank}, {@code building turret}. */
    private static String owner(Rules rules, Weapon weapon) {
        for (UnitType unit : rules.units()) if (unit.weapon() == weapon) return "unit " + unit.id();
        for (BuildingType building : rules.buildings()) if (building.weapon() == weapon) return "building " + building.id();
        throw new IllegalStateException("a weapon of nothing");
    }

    /** The spice of every tile, a byte each (a tile holds at most 255), as Base64. */
    private static String spice(int[] spice) {
        byte[] bytes = new byte[spice.length];
        for (int i = 0; i < spice.length; i++) bytes[i] = (byte) Math.min(255, spice[i]);
        return Base64.getEncoder().encodeToString(bytes);
    }

    // --8<-- [start:read]
    /** The battle a save file holds, on the level of its mission. */
    public static Game read(Map<String, String> values, Rules rules, java.util.function.IntFunction<Level> levels) {
        Snapshot.Reader r = Snapshot.reader(values);
        Level level = levels.apply(r.integer("mission"));
        Set<Side> computer = EnumSet.noneOf(Side.class);
        List<Snapshot.Reader> commanders = r.list("commander");
        for (Snapshot.Reader c : commanders) computer.add(side(c, "side"));
        Game game = new Game(level, rules, r.whole("seed"), computer, false);
        game.random = SeededRandom.resume(r.whole("seed"), r.whole("random"));
        game.time = r.number("time");
        game.thought = r.number("thought");
        game.nextId = r.integer("next");
        game.phase = r.choice("phase", Game.Phase.class);
        String winner = r.text("winner");
        game.winner = winner.equals("none") ? null : Side.ofId(winner);
        int[] spice = new int[game.spice.length];
        byte[] bytes = Base64.getDecoder().decode(r.text("spice"));
        if (bytes.length != spice.length) throw new Snapshot.SnapshotException("the spice is not of this map");
        for (int i = 0; i < spice.length; i++) spice[i] = bytes[i] & 0xFF;
        game.restoreSpice(spice);
        for (Snapshot.Reader c : commanders) {
            Commander commander = game.commanders.get(side(c, "side"));
            commander.nextAttack = c.number("attack");
            commander.wave = c.integer("wave");
            commander.mixed = c.integer("mixed");
            commander.failures = c.integer("failures");
        }
        for (Snapshot.Reader b : r.list("building")) {
            Building building = new Building(b.integer("id"), rules.building(b.text("kind")), side(b, "side"),
                    b.integer("col"), b.integer("row"), b.number("built"));
            building.health = b.number("health");
            building.turret = building.lastTurret = b.number("turret");
            building.reload = b.number("reload");
            building.sinceShot = b.number("since");
            building.docked = b.integer("docked");
            game.add(building);
        }
        for (Snapshot.Reader u : r.list("unit")) {
            Unit unit = new Unit(u.integer("id"), rules.unit(u.text("kind")), side(u, "side"), u.number("x"),
                    u.number("y"), u.number("angle"));
            unit.turret = unit.lastTurret = u.number("turret");
            unit.health = u.number("health");
            unit.task = u.choice("task", Task.class);
            unit.target = u.integer("target");
            unit.reload = u.number("reload");
            unit.sinceShot = u.number("since");
            unit.load = u.integer("load");
            unit.work = u.number("work");
            unit.stuck = u.number("stuck");
            unit.moving = u.bool("moving");
            unit.harvesting = u.bool("harvesting");
            unit.goal = cell(u, "goal");
            unit.way = cell(u, "way");
            unit.guard = cell(u, "guard");
            unit.field = cell(u, "field");
            game.add(unit);
        }
        for (Snapshot.Reader s : r.list("shot")) {
            Shot shot = new Shot(weapon(rules, s.text("weapon")), side(s, "side"), s.integer("shooter"),
                    s.integer("target"), s.number("x"), s.number("y"), s.number("toX"), s.number("toY"));
            shot.angle = s.number("angle");
            shot.left = s.number("left");
            shot.flight = s.number("flight");
            game.shots.add(shot);
        }
        for (Snapshot.Reader c : r.list("carryall")) {
            String cargo = c.text("cargo");
            Carryall carryall = new Carryall(side(c, "side"), c.number("homeX"), c.number("homeY"), c.number("toX"),
                    c.number("toY"), cargo.equals("none") ? null : rules.unit(cargo));
            carryall.x = carryall.lastX = c.number("x");
            carryall.y = carryall.lastY = c.number("y");
            carryall.angle = c.number("angle");
            game.carryalls.add(carryall);
        }
        if (r.has("worm.x")) {
            Snapshot.Reader m = r.child("worm");
            Worm worm = new Worm(m.number("x"), m.number("y"));
            worm.angle = worm.lastAngle = m.number("angle");
            worm.up = m.bool("up");
            worm.timer = m.number("timer");
            worm.prey = m.integer("prey");
            worm.toX = m.number("toX");
            worm.toY = m.number("toY");
            worm.sniff = m.number("sniff");
            worm.meals = m.integer("meals");
            game.worm = worm;
        }
        game.account();
        for (House house : game.houses.values()) house(r.child("house." + house.side().id()), house, rules);
        game.look();
        return game;
    }
    // --8<-- [end:read]

    private static void house(Snapshot.Reader r, House h, Rules rules) {
        h.credits.pay(h.credits.amount());
        h.credits.refund(r.integer("credits"));
        h.fog.restoreSeen(r.text("seen"));
        h.alarm = r.number("alarm");
        h.attacker = r.integer("attacker");
        h.attackedX = r.number("attackedX");
        h.attackedY = r.number("attackedY");
        h.full = r.number("full");
        h.harvested = r.integer("harvested");
        h.made = r.integer("made");
        h.lost = r.integer("lost");
        h.destroyed = r.integer("destroyed");
        Snapshot.Reader yard = r.child("yard");
        for (Snapshot.Reader order : yard.list("order")) {
            BuildingType type = rules.building(order.text("item"));
            h.yard.order(type, type.cost(), type.seconds());
        }
        h.yard.restore(yard.integer("paid"), yard.number("done"), yard.bool("held"));
        if (!yard.text("ready").equals("none")) h.yard.keep(rules.building(yard.text("ready")));
        Snapshot.Reader factory = r.child("factory");
        for (Snapshot.Reader order : factory.list("order")) {
            UnitType type = rules.unit(order.text("item"));
            h.factory.order(type, type.cost(), type.seconds());
        }
        h.factory.restore(factory.integer("paid"), factory.number("done"), factory.bool("held"));
        if (!factory.text("ready").equals("none")) h.factory.keep(rules.unit(factory.text("ready")));
        for (Snapshot.Reader ghost : r.list("known")) {
            h.known.put(ghost.integer("id"), new Ghost(ghost.integer("id"), rules.building(ghost.text("kind")),
                    side(ghost, "side"), ghost.integer("col"), ghost.integer("row")));
        }
    }

    private static Side side(Snapshot.Reader r, String key) {
        Side side = Side.ofId(r.text(key));
        if (side == null) throw new Snapshot.SnapshotException(key + " is not a side: " + r.text(key));
        return side;
    }

    private static Cell cell(Snapshot.Reader r, String key) {
        if (!r.has(key + ".col")) return null;
        Snapshot.Reader c = r.child(key);
        return new Cell(c.integer("col"), c.integer("row"));
    }

    private static Weapon weapon(Rules rules, String owner) {
        String[] words = owner.split(" ");
        Weapon weapon = words.length != 2 ? null
                : words[0].equals("unit") ? rules.unit(words[1]).weapon() : rules.building(words[1]).weapon();
        if (weapon == null) throw new Snapshot.SnapshotException("no weapon of " + owner);
        return weapon;
    }
}
