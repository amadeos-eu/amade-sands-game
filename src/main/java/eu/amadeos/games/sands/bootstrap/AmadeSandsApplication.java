package eu.amadeos.games.sands.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.games.sands.ui.game.SandsGame;
import eu.amadeos.games.sands.ui.main.SandsPresenter;
import eu.amadeos.games.sands.ui.main.SandsWindow;

public final class AmadeSandsApplication {
    public static final AppInfo APP = new AppInfo("amade-sands-game", "Sands", "sands");

    private AmadeSandsApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeSandsApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> {
            Mixer mixer = injector.getInstance(Mixer.class);
            mixer.start();
            SandsWindow window = injector.getInstance(SandsWindow.class);
            SandsPresenter presenter = injector.getInstance(SandsPresenter.class);
            SandsGame game = injector.getInstance(SandsGame.class);
            window.bind(presenter);
            presenter.start();
            window.whenClosed(() -> {
                game.stop();
                mixer.close();
                System.exit(0);
            });
            window.setLocationByPlatform(true);
            window.setVisible(true);
            game.start(window.canvas());
        }, new SandsModule());
    }
    // --8<-- [end:main]
}
