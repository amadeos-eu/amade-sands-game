package eu.amadeos.games.sands.domain;

/**
 * The numbers of the game that are not in the data files: sizes in logical units (a tile is 16), times in
 * seconds. What the units and buildings can do is in {@code units.txt} and {@code buildings.txt}; what each
 * mission gives the two sides, in {@code missions.txt}.
 */
public final class Field {
    /** The logical size of the screen: the view of the map on the left, the panel on the right. */
    public static final double WIDTH = 640;
    public static final double HEIGHT = 400;
    public static final double PANEL = 128;
    public static final double VIEW_WIDTH = WIDTH - PANEL;
    public static final double VIEW_HEIGHT = HEIGHT;
    public static final int TILE = 16;

    /** How often a computer opponent thinks. */
    public static final double THINK_SECONDS = 1;
    /** A unit this close to where it is going, in units, has arrived. */
    public static final double ARRIVED = 2;
    /** Within this many tiles of its slot, with nothing in the way, a unit drives straight there. */
    public static final double DIRECT = 6;
    /** An idle unit chases an enemy no further than this many tiles from where it was left. */
    public static final double LEASH = 6;
    /** A unit turns this fast, in radians per second, and drives only when it points roughly the right way. */
    public static final double TURN = 5;
    /** Turrets turn this fast, and fire when they point within {@link #AIMED} of the target. */
    public static final double TURRET_TURN = 4;
    public static final double AIMED = 0.15;
    /** On dunes a unit is slower: this part of its speed. */
    public static final double DUNE_SPEED = 0.75;
    /** Buildings may stand this many tiles from a building of their side, at most. */
    public static final int REACH = 2;
    /** Low power: production and turrets at this part of their speed. */
    public static final double LOW_POWER = 0.5;
    /** A building sold brings back this part of its price. */
    public static final double REFUND = 0.5;
    /** Credits a harvester takes from the crystal in a second, and puts into the refinery. */
    public static final double HARVEST_RATE = 30;
    public static final double UNLOAD_RATE = 120;
    /** How far a harvester looks for crystal, in tiles. */
    public static final int CRYSTAL_SEARCH = 24;
    /** The same side is told of an attack again only after this many seconds. */
    public static final double ALARM_SECONDS = 15;
    /** How fast the airlifter flies, in units per second. */
    public static final double AIRLIFTER_SPEED = 90;
    /** Rockets land within this many tiles of their aim. */
    public static final double SCATTER = 0.5;

    /** The burrower: its speed in units per second, how far it smells a unit on the sand, in tiles. */
    public static final double BURROWER_SPEED = 26;
    public static final double BURROWER_SMELL = 12;
    /** Seconds before the first burrower shows, and that it stays under the sand after a meal. */
    public static final double BURROWER_FIRST = 90;
    public static final double BURROWER_DIVE = 45;
    /** It swallows a unit this close, in units. */
    public static final double BURROWER_BITE = 8;

    private Field() {
    }
}
