package eu.amadeos.games.sands.domain;

/**
 * What a vehicle or a turret fires, as the data files describe it. Distances in tiles, times in seconds,
 * the shot's speed in tiles per second.
 *
 * @param splash the radius of a rocket's burst; 0 for a shell, which hits its target only
 */
public record Weapon(Kind kind, double range, double damage, double reload, double speed, double splash) {
    public enum Kind {
        /** A shell flies at its target and hits it. */
        GUN,
        /** A rocket flies further and slower, lands near where it was aimed and bursts there. */
        ROCKET
    }
}
