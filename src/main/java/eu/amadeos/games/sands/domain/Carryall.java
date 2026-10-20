package eu.amadeos.games.sands.domain;

/**
 * The carryall: it flies a new refinery's harvester in from the edge of the map, sets it down at the
 * refinery, and flies off again. It cannot be shot at.
 */
public final class Carryall {
    final Side side;
    double x;
    double y;
    double lastX;
    double lastY;
    double angle;
    double toX;
    double toY;
    /** Where it came from and flies back to. */
    final double homeX;
    final double homeY;
    /** What it carries, {@code null} on the way back. */
    UnitType cargo;

    Carryall(Side side, double x, double y, double toX, double toY, UnitType cargo) {
        this.side = side;
        this.x = x;
        this.y = y;
        this.lastX = x;
        this.lastY = y;
        this.homeX = x;
        this.homeY = y;
        this.toX = toX;
        this.toY = toY;
        this.cargo = cargo;
        this.angle = Math.atan2(toY - y, toX - x);
    }

    void remember() {
        lastX = x;
        lastY = y;
    }

    public Side side() {
        return side;
    }

    public double x(double alpha) {
        return lastX + (x - lastX) * alpha;
    }

    public double y(double alpha) {
        return lastY + (y - lastY) * alpha;
    }

    public double angle() {
        return angle;
    }

    public UnitType cargo() {
        return cargo;
    }
}
