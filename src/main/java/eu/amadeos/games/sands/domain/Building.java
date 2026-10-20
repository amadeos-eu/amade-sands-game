package eu.amadeos.games.sands.domain;

import eu.amadeos.gameengine.math.Angles;
import eu.amadeos.gameengine.tile.Cell;

/** A building on its tiles: {@code col}, {@code row} is its top left one. A turret turns and fires. */
public final class Building implements Thing {
    private final int id;
    private final BuildingType type;
    private final Side side;
    private final int col;
    private final int row;
    double health;
    double turret = Math.PI / 2;
    double lastTurret = Math.PI / 2;
    double reload;
    double sinceShot = Double.MAX_VALUE;
    /** The harvester on a refinery's pad, 0 for none. */
    int docked;
    /** When it went up, in game seconds. */
    final double built;

    Building(int id, BuildingType type, Side side, int col, int row, double built) {
        this.id = id;
        this.type = type;
        this.side = side;
        this.col = col;
        this.row = row;
        this.health = type.health();
        this.built = built;
    }

    @Override
    public int id() {
        return id;
    }

    public BuildingType type() {
        return type;
    }

    @Override
    public Side side() {
        return side;
    }

    public int col() {
        return col;
    }

    public int row() {
        return row;
    }

    /** The middle of the building, in units. */
    @Override
    public double x() {
        return (col + type.width() / 2.0) * Field.TILE;
    }

    @Override
    public double y() {
        return (row + type.height() / 2.0) * Field.TILE;
    }

    @Override
    public double health() {
        return health;
    }

    @Override
    public double maxHealth() {
        return type.health();
    }

    @Override
    public double radius() {
        return Math.max(type.width(), type.height()) * Field.TILE / 2.0;
    }

    public double turret(double alpha) {
        return lastTurret + Angles.difference(lastTurret, turret) * alpha;
    }

    public double sinceShot() {
        return sinceShot;
    }

    public double built() {
        return built;
    }

    public int docked() {
        return docked;
    }

    /** Whether the building covers a tile. */
    public boolean covers(int c, int r) {
        return c >= col && r >= row && c < col + type.width() && r < row + type.height();
    }

    /** The middle tile, or the one right and below of the middle. */
    public Cell centre() {
        return new Cell(col + type.width() / 2, row + type.height() / 2);
    }

    /** How far a point is from the nearest edge of the building, in units; 0 inside. */
    public double distance(double px, double py) {
        double left = col * Field.TILE;
        double top = row * Field.TILE;
        double dx = Math.max(0, Math.max(left - px, px - (left + type.width() * Field.TILE)));
        double dy = Math.max(0, Math.max(top - py, py - (top + type.height() * Field.TILE)));
        return Math.hypot(dx, dy);
    }

    /** The middle of the pad a harvester unloads on, in units. */
    public double dockX() {
        return (col + type.dock() + 0.5) * Field.TILE;
    }

    public double dockY() {
        return (row + type.height() / 2.0) * Field.TILE;
    }

    @Override
    public String toString() {
        return type.id() + " " + id + " (" + side.id() + ") at " + col + ", " + row;
    }
}
