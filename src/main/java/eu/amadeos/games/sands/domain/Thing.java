package eu.amadeos.games.sands.domain;

/** What can be shot at: a vehicle or a building. Positions in units, the middle of the thing. */
public sealed interface Thing permits Unit, Building {
    /** Unique in a game, and kept in a save file: orders aim at things by their id. */
    int id();

    Side side();

    double x();

    double y();

    double health();

    double maxHealth();

    /** How far from its middle a shot or a burst reaches it, in units. */
    double radius();

    default boolean alive() {
        return health() > 0;
    }
}
