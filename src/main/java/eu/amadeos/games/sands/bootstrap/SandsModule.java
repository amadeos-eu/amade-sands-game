package eu.amadeos.games.sands.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.games.sands.repository.SandsSettings;
import eu.amadeos.games.sands.repository.Progress;
import eu.amadeos.games.sands.repository.SaveSlot;
import eu.amadeos.games.sands.ui.game.Arena;
import eu.amadeos.games.sands.ui.game.SandsGame;
import eu.amadeos.games.sands.ui.main.SandsPresenter;
import eu.amadeos.games.sands.ui.main.SandsView;
import eu.amadeos.games.sands.ui.main.SandsWindow;

import java.util.Locale;

/**
 * amade-sands-game bindings. Theme, language and the XDG directories (where {@link Progress} and {@link SaveSlot} save) come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class SandsModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.games.sands.messages";

    @Override
    protected void configure() {
        bind(SandsSettings.class).in(Scopes.SINGLETON);
        bind(Progress.class).in(Scopes.SINGLETON);
        bind(SaveSlot.class).in(Scopes.SINGLETON);
        bind(Keyboard.class).in(Scopes.SINGLETON);
        bind(Mouse.class).in(Scopes.SINGLETON);
        bind(Mixer.class).in(Scopes.SINGLETON);
        bind(SandsGame.class).in(Scopes.SINGLETON);
        bind(Arena.class).to(SandsGame.class);
        bind(SandsWindow.class).in(Scopes.SINGLETON);
        bind(SandsView.class).to(SandsWindow.class);
        bind(SandsPresenter.class).in(Scopes.SINGLETON);
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(SandsModule.class, MESSAGES, locale);
    }
}
