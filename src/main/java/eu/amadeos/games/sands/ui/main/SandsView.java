package eu.amadeos.games.sands.ui.main;

import eu.amadeos.gameengine.swing.RenderMode;

/** What {@link SandsPresenter} needs from the window; tests replace it. The battle itself is drawn by the game. */
public interface SandsView {
    /** Shows the settings in the menu. */
    void showOptions(boolean sound, RenderMode mode);

    /** Switches the canvas to a render mode. */
    void useRenderMode(RenderMode mode);

    /** The About dialog. */
    void showAbout();
}
