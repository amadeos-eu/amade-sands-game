package eu.amadeos.games.sands.domain;

import eu.amadeos.gameengine.math.Angles;
import eu.amadeos.gameengine.tile.Cell;

/**
 * A vehicle. It knows where it is and what it is doing; what it does in a step, the {@link Game} decides.
 * Positions in units, angles in radians (0 to the east, the y axis points down). The values of the step
 * before are kept, so that the frames between two steps can draw it in between.
 */
public final class Unit implements Thing {
    private final int id;
    private final UnitType type;
    private final Side side;
    double x;
    double y;
    double lastX;
    double lastY;
    double angle;
    double lastAngle;
    double turret;
    double lastTurret;
    double health;
    Task task = Task.IDLE;
    /** Where it drives: its slot, the spice it goes to; {@code null} for nowhere. */
    Cell goal;
    /** Where its group was sent: the flow field all of them follow. */
    Cell way;
    /** Where it stands guard, and comes back to after a chase. */
    Cell guard;
    /** The id of the enemy it is after, 0 for none. */
    int target;
    double reload;
    double sinceShot = Double.MAX_VALUE;
    /** The credits of spice it carries, and the part of a credit taken in or put out so far. */
    int load;
    double work;
    /** The spice it last harvested, to go back to after unloading. */
    Cell field;
    /** Seconds it has hardly moved while it meant to. */
    double stuck;
    /** Whether it moved in the last step, and is taking in spice. */
    boolean moving;
    boolean harvesting;

    Unit(int id, UnitType type, Side side, double x, double y, double angle) {
        this.id = id;
        this.type = type;
        this.side = side;
        this.x = x;
        this.y = y;
        this.lastX = x;
        this.lastY = y;
        this.angle = angle;
        this.lastAngle = angle;
        this.turret = angle;
        this.lastTurret = angle;
        this.health = type.health();
    }

    /** The start of a step: what is now becomes what was. */
    void remember() {
        lastX = x;
        lastY = y;
        lastAngle = angle;
        lastTurret = turret;
    }

    @Override
    public int id() {
        return id;
    }

    public UnitType type() {
        return type;
    }

    @Override
    public Side side() {
        return side;
    }

    @Override
    public double x() {
        return x;
    }

    @Override
    public double y() {
        return y;
    }

    /** Between the step before ({@code alpha} 0) and this one (1). */
    public double x(double alpha) {
        return lastX + (x - lastX) * alpha;
    }

    public double y(double alpha) {
        return lastY + (y - lastY) * alpha;
    }

    public double angle() {
        return angle;
    }

    public double angle(double alpha) {
        return lastAngle + Angles.difference(lastAngle, angle) * alpha;
    }

    /** Where its gun points; for a vehicle without a turret, where it points itself. */
    public double turret(double alpha) {
        return lastTurret + Angles.difference(lastTurret, turret) * alpha;
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
        return type.size() * Field.TILE;
    }

    public Task task() {
        return task;
    }

    public Cell goal() {
        return goal;
    }

    public int target() {
        return target;
    }

    public int load() {
        return load;
    }

    /** How full a harvester is, 0 to 1. */
    public double fill() {
        return type.capacity() == 0 ? 0 : (double) load / type.capacity();
    }

    public double sinceShot() {
        return sinceShot;
    }

    public boolean moving() {
        return moving;
    }

    public boolean harvesting() {
        return harvesting;
    }

    public Cell cell() {
        return new Cell((int) Math.floor(x / Field.TILE), (int) Math.floor(y / Field.TILE));
    }

    @Override
    public String toString() {
        return type.id() + " " + id + " (" + side.id() + ") at " + Math.round(x) + ", " + Math.round(y);
    }
}
