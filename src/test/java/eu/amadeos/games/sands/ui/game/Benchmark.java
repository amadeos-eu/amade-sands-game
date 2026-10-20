package eu.amadeos.games.sands.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.save.SaveFile;
import eu.amadeos.games.sands.bootstrap.SandsModule;
import eu.amadeos.games.sands.domain.Field;
import eu.amadeos.games.sands.domain.Game;
import eu.amadeos.games.sands.domain.Side;
import eu.amadeos.games.sands.repository.Progress;
import eu.amadeos.games.sands.repository.SaveSlot;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;

/**
 * How long a frame of each mission takes to draw, after ten minutes of the computer playing both sides,
 * at two window sizes: {@code ./gradlew benchmark}. The map scrolls slowly from left to right. The frames
 * are drawn into a compatible image, as the canvas draws, but without waiting for the screen.
 */
public final class Benchmark {
    static final int WARM_UP = 60;
    static final int FRAMES = 300;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("sands");
        Messages msg = Messages.load(SandsModule.class, SandsModule.MESSAGES, Locale.ENGLISH);
        SandsGame game = new SandsGame(new Keyboard(), new Mouse(), msg,
                new Progress(new SaveFile(dir.resolve("progress.save"))),
                new SaveSlot(new SaveFile(dir.resolve("battle.save"))), new Mixer());
        GraphicsConfiguration gc = GraphicsEnvironment.isHeadless() ? null
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        System.out.println("ms per frame               frame  vehicles  shots");
        for (int scale : new int[]{2, 3}) {
            for (int number = 1; number <= game.rules().missions(); number++) {
                SandsGame.PlayScene play = game.play(new Game(game.level(number), game.rules(), number,
                        EnumSet.allOf(Side.class)));
                for (int step = 0; step < 600 * SandsGame.UPDATES_PER_SECOND; step++) play.step(SandsGame.STEP);
                game.settle();
                int w = (int) Field.WIDTH * scale;
                int h = (int) Field.HEIGHT * scale;
                BufferedImage image = gc != null ? gc.createCompatibleImage(w, h) : new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                long total = 0;
                int units = 0;
                int shots = 0;
                for (int i = 0; i < WARM_UP + FRAMES; i++) {
                    play.step(SandsGame.STEP);
                    play.step(SandsGame.STEP);
                    play.camera().moveBy(0.5, 0);
                    Graphics2D g = image.createGraphics();
                    g.scale(scale, scale);
                    long start = System.nanoTime();
                    game.render(g, 0.5);
                    game.renderOverlay(g, 0.5);
                    if (i >= WARM_UP) {
                        total += System.nanoTime() - start;
                        units += play.game().units().size();
                        shots += play.game().shots().size();
                    }
                    g.dispose();
                }
                System.out.printf(Locale.ROOT, "mission %d at %4d x %4d   %6.2f   %5d  %5d%n", number, w, h,
                        total / 1e6 / FRAMES, units / FRAMES, shots / FRAMES);
            }
        }
        System.exit(0);
    }
}
