package eu.amadeos.games.sands.domain;

import eu.amadeos.gameengine.data.TextData.DataException;
import eu.amadeos.gameengine.tile.TileMap;
import eu.amadeos.gameengine.tiled.TiledMap;
import eu.amadeos.gameengine.tiled.TiledObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A mission's map as drawn in Tiled, with the tileset {@code tiles.tsx}. The tile layer {@code ground}
 * holds the sand, dunes, rock, cliffs and crystal by their tiles' classes ({@link Ground}); the object layer
 * {@code things} holds what stands there at the start: an object whose class names a building of
 * {@code buildings.txt} (its top left corner on the tile where the building starts) or a vehicle of
 * {@code units.txt} (on its tile), with the property {@code side}, {@code blue} or {@code red}. Each side
 * starts with a building, or it would have lost before the first step.
 */
public final class Level {
    /** Where the maps and their tileset lie among the classes. */
    public static final String FOLDER = "/eu/amadeos/games/sands/maps/";

    /** Something on the map at the start: a building by its top left tile, a vehicle by its tile. */
    public record Start(String kind, Side side, int col, int row, boolean building) {
    }

    private final Mission mission;
    private final TiledMap map;
    private final TileMap<Ground> ground;
    private final List<Start> starts;

    private Level(Mission mission, TiledMap map, TileMap<Ground> ground, List<Start> starts) {
        this.mission = mission;
        this.map = map;
        this.ground = ground;
        this.starts = List.copyOf(starts);
    }

    /** Mission {@code number}, counted from 1, with its map. */
    public static Level load(int number, Rules rules) {
        Mission mission = rules.mission(number);
        return read(mission, TiledMap.load(Level.class, FOLDER + mission.map()), rules);
    }

    // --8<-- [start:read]
    /** The rules' view of a map: its ground by the tiles' classes, the buildings and vehicles by their objects. */
    public static Level read(Mission mission, TiledMap map, Rules rules) {
        if (map.tileWidth() != Field.TILE || map.tileHeight() != Field.TILE) {
            throw new DataException(map.source() + ": the tiles must be " + Field.TILE + " × " + Field.TILE);
        }
        TileMap<Ground> ground = map.tiles("ground", gid -> Ground.of(map, gid));
        List<Start> starts = new ArrayList<>();
        Set<Side> based = EnumSet.noneOf(Side.class);
        for (TiledObject thing : map.objects("things")) {
            Side side = Side.ofId(thing.properties().string("side", ""));
            if (side == null) throw error(map, thing, "the property side must be blue or red");
            if (rules.isBuilding(thing.type())) {
                BuildingType type = rules.building(thing.type());
                int col = (int) Math.round(thing.x() / Field.TILE);
                int row = (int) Math.round(thing.y() / Field.TILE);
                for (int dy = 0; dy < type.height(); dy++) {
                    for (int dx = 0; dx < type.width(); dx++) {
                        if (!ground.contains(col + dx, row + dy)) throw error(map, thing, "reaches outside the map");
                        if (!ground.get(col + dx, row + dy).buildable()) throw error(map, thing, "stands off the rock");
                    }
                }
                starts.add(new Start(type.id(), side, col, row, true));
                based.add(side);
            } else if (rules.isUnit(thing.type())) {
                int col = (int) Math.floor(thing.centerX() / Field.TILE);
                int row = (int) Math.floor(thing.centerY() / Field.TILE);
                if (!ground.contains(col, row)) throw error(map, thing, "outside the map");
                if (!ground.get(col, row).drivable()) throw error(map, thing, "stands on a cliff");
                starts.add(new Start(thing.type(), side, col, row, false));
            } else {
                throw error(map, thing, "the class \"" + thing.type()
                        + "\" is neither a building of buildings.txt nor a vehicle of units.txt");
            }
        }
        for (Side side : Side.values()) {
            if (!based.contains(side)) throw new DataException(map.source() + ": no building of the side " + side.id());
        }
        return new Level(mission, map, ground, starts);
    }
    // --8<-- [end:read]

    private static DataException error(TiledMap map, TiledObject thing, String message) {
        return new DataException(map.source() + ": object " + thing.id() + " at (" + (int) thing.x() + ", "
                + (int) thing.y() + "): " + message);
    }

    public Mission mission() {
        return mission;
    }

    public int number() {
        return mission.number();
    }

    public TiledMap map() {
        return map;
    }

    public Ground ground(int col, int row) {
        return ground.get(col, row);
    }

    public boolean contains(int col, int row) {
        return ground.contains(col, row);
    }

    /** Columns and rows. */
    public int width() {
        return ground.width();
    }

    public int height() {
        return ground.height();
    }

    public List<Start> starts() {
        return starts;
    }
}
