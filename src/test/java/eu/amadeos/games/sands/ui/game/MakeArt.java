package eu.amadeos.games.sands.ui.game;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws the files made from the game's own art, which are part of the repository: the tileset picture
 * that Tiled shows ({@code maps/tiles.png}: sand, dune, rock, cliff, crystal, thick crystal) and the program
 * icon, a harvester on the crystal, at 48 and 256 pixels. {@code ./gradlew art}, then for Windows:
 *
 * <pre>
 * python3 -c "from PIL import Image; Image.open('build/icon-256.png').save('packaging/windows/amade-sands-game.ico',
 *     sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])"
 * </pre>
 */
public final class MakeArt {
    private static final int[] GROUND = {0xD8A462, 0xE0B06C, 0x84644A, 0x5E4432, 0x4A9A90, 0x2E7C78};

    private MakeArt() {
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(tiles(), "png", new File("src/main/resources/eu/amadeos/games/sands/maps/tiles.png"));
        new File("src/main/resources/icons").mkdirs();
        ImageIO.write(icon(48), "png", new File("src/main/resources/icons/amade-sands-game.png"));
        new File("build").mkdirs();
        ImageIO.write(icon(256), "png", new File("build/icon-256.png"));
    }

    /** Six tiles of 16 × 16 pixels in a row, as the ground of the maps counts them from 0: plain, with a grain. */
    static BufferedImage tiles() {
        BufferedImage image = new BufferedImage(16 * GROUND.length, 16, BufferedImage.TYPE_INT_ARGB);
        for (int kind = 0; kind < GROUND.length; kind++) {
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    double grain = Art.fbm(x / 4.0, y / 4.0, kind + 1) - 0.5;
                    if (kind == 1) grain += 0.25 * Math.sin((x + y * 0.4) * 0.8);   // the ripples of a dune
                    image.setRGB(kind * 16 + x, y, 0xFF000000 | Art.shade(GROUND[kind], 1 + 0.3 * grain));
                }
            }
        }
        return image;
    }

    /** A harvester seen from above on a round patch of sand and crystal. */
    static BufferedImage icon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(size / 24.0, size / 24.0);
        Ellipse2D.Double patch = new Ellipse2D.Double(0.5, 0.5, 23, 23);
        g.setPaint(new RadialGradientPaint(9, 8, 16, new float[]{0, 0.55f, 1},
                new Color[]{new Color(0xE8C890), new Color(0x58A898), new Color(0x1E5E58)}));
        g.fill(patch);
        g.translate(12, 12.5);
        g.rotate(-0.75);
        g.scale(0.56, 0.56);
        g.translate(-0.5, 0);
        Art.harvester(g, Art.BLUE, 0.8);
        g.dispose();
        return image;
    }
}
