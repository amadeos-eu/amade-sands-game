package eu.amadeos.games.sands.domain;

/** What a vehicle is doing. */
public enum Task {
    /** Standing guard: it fires at what comes near and chases it a little way, then comes back. */
    IDLE,
    /** Driving to its slot, firing on the way if its gun turns. */
    MOVE,
    /** After one enemy, until it is destroyed or out of sight. */
    ATTACK,
    /** Driving to a place and fighting everything it meets on the way: the computer's attacks. */
    HUNT,
    /** A harvester on its way to the crystal, or taking it in. */
    HARVEST,
    /** A harvester driving to a refinery with its load. */
    RETURN,
    /** A harvester on a refinery's pad, unloading. */
    UNLOAD
}
