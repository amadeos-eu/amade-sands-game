package eu.amadeos.games.sands.repository;

import eu.amadeos.gameengine.save.SaveFile;
import eu.amadeos.games.sands.domain.Archive;
import eu.amadeos.games.sands.domain.Game;
import eu.amadeos.games.sands.domain.Level;
import eu.amadeos.games.sands.domain.Rules;
import eu.amadeos.games.sands.domain.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveSlotTest {
    private static final Rules RULES = Rules.load();

    @TempDir
    Path dir;

    @Test
    void aSavedBattleLoadsBack() {
        SaveSlot slot = new SaveSlot(new SaveFile(dir.resolve("battle.save")));
        assertFalse(slot.exists());
        assertTrue(slot.load(RULES).isEmpty());
        Game game = new Game(Level.load(2, RULES), RULES, 7, EnumSet.of(Side.RED));
        for (int i = 0; i < 1200; i++) game.update(1.0 / 120);
        slot.save(game);
        assertTrue(slot.exists());
        Game loaded = slot.load(RULES).orElseThrow();
        assertEquals(2, loaded.level().number());
        assertEquals(game.time(), loaded.time(), 1e-9);
        assertEquals(game.units().size(), loaded.units().size());
        assertEquals(Archive.write(game), Archive.write(loaded));
    }

    @Test
    void aBrokenOrOlderFileLoadsNothing() throws Exception {
        SaveFile file = new SaveFile(dir.resolve("battle.save"));
        SaveSlot slot = new SaveSlot(file);
        file.store(Archive.VERSION + 1, Map.of("mission", "1"));
        assertTrue(slot.load(RULES).isEmpty(), "another version");
        file.store(Archive.VERSION, Map.of("mission", "1", "units", "nonsense"));
        assertTrue(slot.load(RULES).isEmpty(), "values that make no battle");
        Files.writeString(dir.resolve("battle.save"), "not a save file at all");
        assertTrue(slot.load(RULES).isEmpty(), "a file that is no save");
    }
}
