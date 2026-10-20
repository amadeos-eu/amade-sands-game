package eu.amadeos.games.sands.domain;

import eu.amadeos.gameengine.math.Angles;

/**
 * The sand worm: it moves under the open sand, smells the vehicles on it, and swallows them whole. Then
 * it dives, and comes up again somewhere else. It cannot cross rock: on rock, a vehicle is safe.
 */
public final class Worm {
    double x;
    double y;
    double lastX;
    double lastY;
    double angle;
    double lastAngle;
    /** Above the sand, or under it until {@code timer} runs out. */
    boolean up;
    double timer;
    /** The vehicle it hunts, 0 for none; where it wanders without one. */
    int prey;
    double toX;
    double toY;
    double sniff;
    int meals;

    Worm(double x, double y) {
        this.x = x;
        this.y = y;
        this.lastX = x;
        this.lastY = y;
        this.up = true;
    }

    void remember() {
        lastX = x;
        lastY = y;
        lastAngle = angle;
    }

    public double x(double alpha) {
        return lastX + (x - lastX) * alpha;
    }

    public double y(double alpha) {
        return lastY + (y - lastY) * alpha;
    }

    public double angle(double alpha) {
        return lastAngle + Angles.difference(lastAngle, angle) * alpha;
    }

    public boolean up() {
        return up;
    }

    public int prey() {
        return prey;
    }

    public int meals() {
        return meals;
    }
}
