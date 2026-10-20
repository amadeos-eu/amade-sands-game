package eu.amadeos.games.sands.domain;

/**
 * A shell or a rocket in flight. A shell follows its target and hits it; a rocket flies to the point it was
 * aimed at, a little beside it, and bursts there.
 */
public final class Shot {
    final Weapon weapon;
    final Side side;
    /** Who fired it, and the target of a shell, 0 for a rocket. */
    final int shooter;
    final int target;
    double x;
    double y;
    double lastX;
    double lastY;
    double toX;
    double toY;
    double angle;
    /** Seconds of flight left, and the whole flight's. */
    double left;
    double flight;

    Shot(Weapon weapon, Side side, int shooter, int target, double x, double y, double toX, double toY) {
        this.weapon = weapon;
        this.side = side;
        this.shooter = shooter;
        this.target = target;
        this.x = x;
        this.y = y;
        this.lastX = x;
        this.lastY = y;
        this.toX = toX;
        this.toY = toY;
        this.angle = Math.atan2(toY - y, toX - x);
        this.flight = Math.max(0.05, Math.hypot(toX - x, toY - y) / (weapon.speed() * Field.TILE));
        this.left = flight;
    }

    void remember() {
        lastX = x;
        lastY = y;
    }

    public Weapon.Kind kind() {
        return weapon.kind();
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

    /** How far it has flown, 0 at the muzzle and 1 where it lands. */
    public double along() {
        return 1 - left / flight;
    }
}
