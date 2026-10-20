package eu.amadeos.games.sands.domain;

import eu.amadeos.gameengine.data.TextData.DataException;
import eu.amadeos.gameengine.tiled.TiledMap;

/**
 * What a tile of a map is to the rules, by the class its tile has in Tiled's tileset: open {@code sand},
 * {@code dune}s that slow the vehicles down, {@code rock} to build on, {@code cliff}s nothing crosses, and
 * {@code spice} and {@code thick} spice on the sand, to harvest. A tile without a class is sand.
 */
// --8<-- [start:ground]
public enum Ground {
    SAND(0), DUNE(0), ROCK(0), CLIFF(0), SPICE(100), THICK(250);

    /** The credits of spice a tile of this ground holds at the start. */
    private final int spice;

    Ground(int spice) {
        this.spice = spice;
    }

    public int spice() {
        return spice;
    }

    /** Vehicles drive anywhere but over cliffs. */
    public boolean drivable() {
        return this != CLIFF;
    }

    /** Buildings stand on rock only: sand gives way. */
    public boolean buildable() {
        return this == ROCK;
    }

    /** The worm lives in the sand and cannot go onto rock. */
    public boolean sandy() {
        return this != ROCK && this != CLIFF;
    }

    /** The ground a gid of the map stands for; an unknown class is an error naming the map. */
    static Ground of(TiledMap map, int gid) {
        return switch (map.type(gid)) {
            case "", "sand" -> SAND;
            case "dune" -> DUNE;
            case "rock" -> ROCK;
            case "cliff" -> CLIFF;
            case "spice" -> SPICE;
            case "thick" -> THICK;
            default -> throw new DataException(map.source() + ": the tile class \"" + map.type(gid)
                    + "\" means nothing to the game; use sand, dune, rock, cliff, spice or thick");
        };
    }
}
// --8<-- [end:ground]
