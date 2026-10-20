package eu.amadeos.games.sands.ui.main;

import com.google.inject.Inject;
import eu.amadeos.gameengine.swing.RenderMode;
import eu.amadeos.games.sands.repository.SandsSettings;
import eu.amadeos.games.sands.ui.game.Arena;

/**
 * Everything around the arena, without Swing: the menu commands, the settings, and pausing when the window
 * loses the focus. All methods run on the Swing thread. The progress through the missions and the saved
 * battle are the game's own.
 */
public final class SandsPresenter {
    private final SandsView view;
    private final Arena arena;
    private final SandsSettings settings;

    @Inject
    public SandsPresenter(SandsView view, Arena arena, SandsSettings settings) {
        this.view = view;
        this.arena = arena;
        this.settings = settings;
    }

    public void start() {
        boolean sound = settings.sound();
        RenderMode mode = settings.renderMode();
        view.showOptions(sound, mode);
        view.useRenderMode(mode);
        arena.setSound(sound);
        arena.showTitle();
    }

    public void newGame() {
        arena.chooseLevel();
    }

    public void pause() {
        arena.pause();
    }

    /** The window lost the focus: nobody is at the mouse, so a running battle waits. */
    public void focusLost() {
        arena.pause();
    }

    public void setSound(boolean on) {
        settings.setSound(on);
        arena.setSound(on);
    }

    public void setRenderMode(RenderMode mode) {
        settings.setRenderMode(mode);
        view.useRenderMode(mode);
    }

    public void showAbout() {
        view.showAbout();
    }
}
