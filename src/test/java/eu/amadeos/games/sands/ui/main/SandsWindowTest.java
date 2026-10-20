package eu.amadeos.games.sands.ui.main;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.save.SaveFile;
import eu.amadeos.gameengine.swing.RenderMode;
import eu.amadeos.games.sands.bootstrap.SandsModule;
import eu.amadeos.games.sands.repository.SandsSettings;
import eu.amadeos.games.sands.repository.Progress;
import eu.amadeos.games.sands.repository.SaveSlot;
import eu.amadeos.games.sands.ui.game.SandsGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenuItem;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The real window, game and loop: keys on the canvas reach the menu, the graphics menu reaches the canvas. */
class SandsWindowTest {
    @TempDir
    Path dir;

    private SandsWindow window;
    private SandsGame game;
    private SandsSettings settings;

    @BeforeEach
    void open() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        Messages msg = Messages.load(SandsModule.class, SandsModule.MESSAGES, Locale.ENGLISH);
        settings = new SandsSettings(dir.resolve("sands.properties"));
        Keyboard keyboard = new Keyboard();
        Mouse mouse = new Mouse();
        onEdt(() -> {
            game = new SandsGame(keyboard, mouse, msg, new Progress(new SaveFile(dir.resolve("progress.save"))),
                    new SaveSlot(new SaveFile(dir.resolve("battle.save"))), new Mixer());
            window = new SandsWindow(msg, game, keyboard, mouse);
            SandsPresenter presenter = new SandsPresenter(window, game, settings);
            window.bind(presenter);
            presenter.start();
            window.setVisible(true);
            game.start(window.canvas());
        });
    }

    @AfterEach
    void close() throws Exception {
        if (game != null) game.stop();
        if (window != null) onEdt(window::dispose);
    }

    private static void onEdt(Runnable code) throws Exception {
        try {
            SwingUtilities.invokeAndWait(code);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error error) throw error;
            throw (Exception) e.getCause();
        }
    }

    private void key(int code) throws Exception {
        onEdt(() -> window.canvas().dispatchEvent(new KeyEvent(window.canvas(), KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED)));
    }

    private JMenuItem item(String name) {
        return find(window.getJMenuBar(), name);
    }

    private static JMenuItem find(MenuElement element, String name) {
        for (MenuElement child : element.getSubElements()) {
            if (child instanceof JMenuItem item && name.equals(item.getName())) return item;
            JMenuItem found = find(child, name);
            if (found != null) return found;
        }
        return null;
    }

    @Test
    void theSavedRenderModeIsUsedAndTheMenuChangesIt() throws Exception {
        onEdt(() -> {
            assertEquals(RenderMode.DIRECT, window.canvas().renderMode());
            assertTrue(item("game-graphics-direct").isSelected());
            JMenuItem pixel = item("game-graphics-pixel");
            assertNotNull(pixel);
            pixel.doClick(0);
            assertEquals(RenderMode.PIXEL, window.canvas().renderMode());
        });
        assertEquals(RenderMode.PIXEL, settings.renderMode());
    }

    @Test
    void f12ShowsTheFrameTimesAndF11SwitchesToFullScreen() throws Exception {
        key(KeyEvent.VK_F12);
        onEdt(() -> assertTrue(window.canvas().showsFrameTimes()));
        key(KeyEvent.VK_F11);
        onEdt(() -> assertTrue(window.fullScreen().active(), "full screen"));
        key(KeyEvent.VK_F11);
        onEdt(() -> assertFalse(window.fullScreen().active(), "back in the window"));
        long deadline = System.currentTimeMillis() + 3000;
        while (window.canvas().frameTimes().framesPerSecond() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(window.canvas().frameTimes().framesPerSecond() > 0, "frames are drawn and measured");
    }
}
