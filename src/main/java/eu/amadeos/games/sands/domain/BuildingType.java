package eu.amadeos.games.sands.domain;

import java.util.List;

/**
 * A kind of building, as {@code buildings.txt} describes it. Sizes and distances in tiles.
 *
 * @param cost    0 for a building the yard does not build, the yard itself
 * @param power   what it makes, a turbine; negative for what it uses
 * @param storage the credits of crystal it stores
 * @param dock    the column, from its left, of the pad a harvester unloads on; -1 for none. A building
 *                with a dock comes with a harvester, and its pad is open ground
 * @param weapon  {@code null} for a building that does not fire
 */
public record BuildingType(String id, int width, int height, double health, double armour, int cost, double seconds,
                           int power, double sight, int storage, List<String> needs, boolean yard, boolean factory,
                           boolean radar, int dock, Weapon weapon) {
    public BuildingType {
        needs = List.copyOf(needs);
    }

    /** Whether the construction yard builds it. */
    public boolean buildable() {
        return cost > 0;
    }

    /** Whether the tile {@code dx}, {@code dy} from its top left corner is a wall, not its pad. */
    public boolean solid(int dx, int dy) {
        return dx != dock;
    }
}
