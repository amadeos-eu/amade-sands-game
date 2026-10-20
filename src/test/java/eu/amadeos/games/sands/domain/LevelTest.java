package eu.amadeos.games.sands.domain;

import eu.amadeos.gameengine.data.TextData;
import eu.amadeos.gameengine.tiled.TiledMap;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelTest {
    private final Rules rules = Rules.load();

    @Test
    void everyMapGivesBothSidesAYardOnRockAndCrystal() {
        for (int number = 1; number <= rules.missions(); number++) {
            Level level = Level.load(number, rules);
            for (Side side : Side.values()) {
                assertEquals(1, level.starts().stream().filter(s -> s.side() == side && s.kind().equals("yard")).count(),
                        "map " + number + ": one yard for " + side);
            }
            Game game = new Game(level, rules, 1, Set.of());
            int crystal = 0;
            for (int row = 0; row < level.height(); row++) {
                for (int col = 0; col < level.width(); col++) crystal += game.crystal(col, row);
            }
            assertTrue(crystal > 10_000, "map " + number + ": crystal enough for a battle, " + crystal);
        }
    }

    @Test
    void aBuildingOffTheRockIsAMistakeInTheMap() {
        Mission mission = rules.mission(1);
        TiledMap map = TiledMap.load(Level.class, Level.FOLDER + mission.map());
        Level level = Level.read(mission, map, rules);
        assertTrue(level.starts().stream().anyMatch(Level.Start::building));
        TiledMap broken = TiledMap.load(Level.class, Level.FOLDER + "broken.tmx");    // the yard moved onto sand
        assertEquals("broken.tmx: object 1 at (320, 0): stands off the rock", assertThrows(TextData.DataException.class,
                () -> Level.read(mission, broken, rules)).getMessage());
    }
}
