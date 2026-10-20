package eu.amadeos.games.sands.domain;

/** What happened in a step, for the sounds, the pictures and the messages; the rules have dealt with it. */
// --8<-- [start:event]
public sealed interface Event {
    /** The yard finished a building, which waits to be placed. */
    record Ready(Side side, BuildingType type) implements Event {
    }

    record Placed(Building building) implements Event {
    }

    record Sold(Building building, int refund) implements Event {
    }

    /** A vehicle left the factory, or the carryall set it down. */
    record Deployed(Unit unit) implements Event {
    }

    record Fired(double x, double y, double angle, Weapon.Kind kind, Side side) implements Event {
    }

    /** A shell hit. */
    record Hit(double x, double y) implements Event {
    }

    /** A rocket burst; {@code radius} in units. */
    record Burst(double x, double y, double radius) implements Event {
    }

    record Destroyed(Unit unit) implements Event {
    }

    record Collapsed(Building building) implements Event {
    }

    /** A harvester put spice into the refinery. */
    record Unloaded(Unit harvester, int credits) implements Event {
    }

    /** More spice came home than the refineries and silos store. */
    record StorageFull(Side side) implements Event {
    }

    /** Something of {@code side} was hit, at {@code x}, {@code y}; not again for a while. */
    record UnderAttack(Side side, double x, double y) implements Event {
    }

    record WormRose(double x, double y) implements Event {
    }

    record WormAte(Unit unit) implements Event {
    }

    /** The game is over: {@code winner} has the only buildings left. */
    record Over(Side winner) implements Event {
    }
}
// --8<-- [end:event]
