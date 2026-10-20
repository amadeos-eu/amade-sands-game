package eu.amadeos.games.sands.domain;

import java.util.List;

/**
 * A kind of vehicle, as {@code units.txt} describes it. Distances in tiles, speeds in tiles per second.
 *
 * @param weapon   {@code null} for a vehicle without one, the harvester
 * @param needs    the ids of the buildings its side must have before a factory makes it
 * @param capacity the credits of spice it carries; 0 for all but the harvester
 */
public record UnitType(String id, double health, double armour, double speed, double sight, double size, int cost,
                       double seconds, List<String> needs, Weapon weapon, boolean turret, int capacity) {
    public UnitType {
        needs = List.copyOf(needs);
    }

    public boolean armed() {
        return weapon != null;
    }

    public boolean harvests() {
        return capacity > 0;
    }
}
