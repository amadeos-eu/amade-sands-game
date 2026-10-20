package eu.amadeos.games.sands.domain;

import eu.amadeos.gameengine.economy.Credits;
import eu.amadeos.gameengine.economy.Production;
import eu.amadeos.gameengine.fog.FogOfWar;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One side's household: its credits and their storage, what its yard builds and its factory makes, its
 * power, what it sees, the enemy buildings it knows of, and a few numbers for the end.
 */
public final class House {
    private final Side side;
    final Credits credits;
    final Production<BuildingType> yard = new Production<>();
    final Production<UnitType> factory = new Production<>();
    final FogOfWar fog;
    /** The enemy buildings this side has seen, by id, in the order it saw them. */
    final Map<Integer, Ghost> known = new LinkedHashMap<>();
    int powerMade;
    int powerUsed;
    /** When it was last told it is under attack, by whom and where. */
    double alarm = -Double.MAX_VALUE;
    int attacker;
    double attackedX;
    double attackedY;
    double full = -Double.MAX_VALUE;
    int harvested;
    int made;
    int lost;
    int destroyed;

    House(Side side, int credits, int width, int height) {
        this.side = side;
        this.credits = new Credits(credits, 0);
        this.fog = new FogOfWar(width, height);
    }

    public Side side() {
        return side;
    }

    public int credits() {
        return credits.amount();
    }

    /** How many credits its refineries and silos store. */
    public int storage() {
        return credits.capacity();
    }

    public Production<BuildingType> yard() {
        return yard;
    }

    public Production<UnitType> factory() {
        return factory;
    }

    public FogOfWar fog() {
        return fog;
    }

    public int powerMade() {
        return powerMade;
    }

    public int powerUsed() {
        return powerUsed;
    }

    /** Less power made than used: everything works at half speed. */
    public boolean lowPower() {
        return powerUsed > powerMade;
    }

    public Collection<Ghost> known() {
        return Collections.unmodifiableCollection(known.values());
    }

    /** Credits of crystal brought home, vehicles made, lost and enemy things destroyed. */
    public int harvested() {
        return harvested;
    }

    public int made() {
        return made;
    }

    public int lost() {
        return lost;
    }

    public int destroyed() {
        return destroyed;
    }
}
