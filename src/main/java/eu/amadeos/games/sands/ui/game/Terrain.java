package eu.amadeos.games.sands.ui.game;

import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.games.sands.domain.Field;
import eu.amadeos.games.sands.domain.Game;
import eu.amadeos.games.sands.domain.Ground;
import eu.amadeos.games.sands.domain.Level;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The desert of a mission: dunes with ripples, rock with cracks and the cliffs' shadows, scree at the foot of
 * the rock, and the crystal. Every pixel is worked out on its own, from noise and from the tiles of the map,
 * with light from the top left: that is what makes the ground look like sand and stone, and it takes a
 * while, a second or two for a whole map. So it is done on other threads, in squares of {@value #CHUNK} ×
 * {@value #CHUNK} tiles, and a square shows the tiles' plain colours until its picture is ready.
 *
 * <p>The crystal is harvested, so it changes. Each square keeps its ground without crystal and the colour the
 * crystal would have on each pixel, both lit already; when the crystal under a square changes by a step, the
 * two are mixed again, which is quick, and still done on another thread.
 */
// --8<-- [start:terrain]
final class Terrain {
    static final int CHUNK = 8;
    /** Pixels for each unit of the world: the ground is as sharp on a screen of twice the logical size. */
    static final int PIXELS = 2;
    /** The steps of crystal on a tile that the ground shows: harvesting changes the picture now and then, not in every step. */
    static final int STEPS = 16;
    private static final int SIZE = CHUNK * Field.TILE;
    private static final int SIDE = SIZE * PIXELS;
    /** Pixels around a square that its shadows and slopes look at. */
    private static final int BORDER = 20;
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() - 1), task -> {
                Thread thread = new Thread(task, "sands-terrain");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });

    private final Level level;
    private final int across;
    private final Chunk[] chunks;
    private final float[] rock;
    private final float[] cliff;
    private final float[] dune;
    private final Rectangle2D.Double rectangle = new Rectangle2D.Double();
    private final Color[] plain = new Color[Ground.values().length];
    private Game game;
    private int version;

    /** One square: its ground without crystal, and the picture with the crystal as it was last mixed in. */
    private static final class Chunk {
        final int col;
        final int row;
        Future<?> work;
        /** Ground and crystal, lit; and how much of the crystal's colour each pixel may take. */
        volatile int[] ground;
        volatile int[] crystal;
        volatile float[] weight;
        /** The crystal on the square and a tile around it, in steps: as mixed in, and as asked for. */
        byte[] mixed;
        byte[] wanted;
        final AtomicReference<BufferedImage> ready = new AtomicReference<>();
        Sprite shown;

        Chunk(int col, int row) {
            this.col = col;
            this.row = row;
        }
    }

    Terrain(Level level) {
        this.level = level;
        this.across = (level.width() + CHUNK - 1) / CHUNK;
        int down = (level.height() + CHUNK - 1) / CHUNK;
        int tiles = level.width() * level.height();
        rock = new float[tiles];
        cliff = new float[tiles];
        dune = new float[tiles];
        for (int row = 0; row < level.height(); row++) {
            for (int col = 0; col < level.width(); col++) {
                Ground ground = level.ground(col, row);
                int i = row * level.width() + col;
                rock[i] = ground == Ground.ROCK || ground == Ground.CLIFF ? 1 : 0;
                cliff[i] = ground == Ground.CLIFF ? 1 : 0;
                dune[i] = ground == Ground.DUNE ? 1 : 0;
            }
        }
        plain[Ground.SAND.ordinal()] = new Color(0xD8A462);
        plain[Ground.DUNE.ordinal()] = new Color(0xE0B06C);
        plain[Ground.ROCK.ordinal()] = new Color(0x84644A);
        plain[Ground.CLIFF.ordinal()] = new Color(0x5E4432);
        plain[Ground.CRYSTAL.ordinal()] = new Color(0x4A9A90);
        plain[Ground.THICK.ordinal()] = new Color(0x2E7C78);
        chunks = new Chunk[across * down];
        for (int i = 0; i < chunks.length; i++) chunks[i] = new Chunk(i % across * CHUNK, i / across * CHUNK);
    }
    // --8<-- [end:terrain]

    // --8<-- [start:update]
    /**
     * After every step: a square whose crystal has changed by a step is mixed again. The same terrain serves
     * every game on its map, the title's and the player's, so a new game is a change, too.
     */
    void update(Game game) {
        boolean changed = game != this.game || game.crystalVersion() != version;
        this.game = game;
        version = game.crystalVersion();
        for (Chunk chunk : chunks) {
            if (changed) chunk.wanted = crystal(game, chunk);
            boolean idle = chunk.work == null || chunk.work.isDone();
            if (idle && !Arrays.equals(chunk.mixed, chunk.wanted)) schedule(chunk);
        }
    }
    // --8<-- [end:update]

    /** The crystal on a square and a tile around it, in steps. */
    private byte[] crystal(Game game, Chunk chunk) {
        byte[] steps = new byte[(CHUNK + 2) * (CHUNK + 2)];
        int i = 0;
        for (int row = chunk.row - 1; row <= chunk.row + CHUNK; row++) {
            for (int col = chunk.col - 1; col <= chunk.col + CHUNK; col++) {
                steps[i++] = (byte) (level.contains(col, row) ? step(game.crystal(col, row)) : 0);
            }
        }
        return steps;
    }

    static int step(int crystal) {
        if (crystal <= 0) return 0;
        return Math.clamp(Math.round(crystal * (float) STEPS / Ground.THICK.crystal()), 1, STEPS);
    }

    private void schedule(Chunk chunk) {
        byte[] crystal = chunk.wanted != null ? chunk.wanted : new byte[(CHUNK + 2) * (CHUNK + 2)];
        if (chunk.wanted == null) chunk.wanted = crystal;
        chunk.work = WORKERS.submit(() -> {
            if (chunk.ground == null) draw(chunk);
            BufferedImage image = mix(chunk, crystal);
            chunk.mixed = crystal;
            chunk.ready.set(image);
        });
    }

    // --8<-- [start:render]
    /**
     * The squares the camera sees: as a picture when one is ready, else in the plain colours of their tiles.
     */
    void render(Graphics2D g, Camera camera, double alpha) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (Chunk chunk : chunks) {
            BufferedImage image = chunk.ready.getAndSet(null);
            if (image != null) {
                chunk.shown = Sprite.of(image, image.getWidth() / (double) PIXELS, image.getHeight() / (double) PIXELS);
            }
            double x = chunk.col * Field.TILE;
            double y = chunk.row * Field.TILE;
            if (!camera.sees(x, y, SIZE, SIZE, alpha)) continue;
            if (chunk.shown != null) chunk.shown.drawSeamless(g, x, y);
            else plain(g, chunk);
        }
    }
    // --8<-- [end:render]

    private void plain(Graphics2D g, Chunk chunk) {
        for (int row = chunk.row; row < Math.min(level.height(), chunk.row + CHUNK); row++) {
            for (int col = chunk.col; col < Math.min(level.width(), chunk.col + CHUNK); col++) {
                g.setColor(plain[level.ground(col, row).ordinal()]);
                rectangle.setRect(col * Field.TILE, row * Field.TILE, Field.TILE, Field.TILE);
                g.fill(rectangle);
            }
        }
    }

    /** Whether every square shows its picture with the crystal as it is: for the tests. */
    boolean settled() {
        for (Chunk chunk : chunks) {
            if (chunk.shown == null || chunk.ready.get() != null || !Arrays.equals(chunk.mixed, chunk.wanted)) {
                return false;
            }
        }
        return true;
    }

    /** Waits until every square's picture is ready, and shows them: for screenshots and tests, which draw one frame. */
    void settle() {
        for (int round = 0; round < 3; round++) {
            for (Chunk chunk : chunks) {
                try {
                    if (chunk.work == null) schedule(chunk);
                    chunk.work.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new IllegalStateException(e.getCause());
                }
                BufferedImage image = chunk.ready.getAndSet(null);
                if (image != null) {
                    chunk.shown = Sprite.of(image, image.getWidth() / (double) PIXELS,
                            image.getHeight() / (double) PIXELS);
                }
                if (!Arrays.equals(chunk.mixed, chunk.wanted)) schedule(chunk);
            }
        }
    }

    int across() {
        return across;
    }

    // ---- the fields of the map, in world units ----------------------------------------------------

    /**
     * A field of the tiles, smooth between their middles: 0.5 halfway from a tile that has it to one that has
     * not. The steps between the middles are eased, so that no slope breaks at a tile's middle: the light
     * would show the grid.
     */
    private double field(float[] tiles, double wx, double wy) {
        double fx = wx / Field.TILE - 0.5;
        double fy = wy / Field.TILE - 0.5;
        int c = (int) Math.floor(fx);
        int r = (int) Math.floor(fy);
        double tx = fx - c;
        double ty = fy - r;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        double a = tile(tiles, c, r);
        double b = tile(tiles, c + 1, r);
        double d = tile(tiles, c, r + 1);
        double e = tile(tiles, c + 1, r + 1);
        double top = a + (b - a) * tx;
        return top + (d + (e - d) * tx - top) * ty;
    }

    private float tile(float[] tiles, int col, int row) {
        col = Math.clamp(col, 0, level.width() - 1);
        row = Math.clamp(row, 0, level.height() - 1);
        return tiles[row * level.width() + col];
    }

    /** The edges of the rock and the crystal frayed by noise, so that they do not follow the tiles. */
    private static double warpX(double wx, double wy) {
        return wx + (Art.fbm(wx / 22.0, wy / 22.0, 51) - 0.5) * 18;
    }

    private static double warpY(double wx, double wy) {
        return wy + (Art.fbm(wx / 22.0, wy / 22.0, 53) - 0.5) * 18;
    }

    /** Above 0 is rock. */
    private double rock(double wx, double wy) {
        return field(rock, warpX(wx, wy), warpY(wx, wy)) - 0.5;
    }

    // --8<-- [start:draw]
    /** A square's ground, without the crystal, and the crystal's colour on it: the slow part. */
    private void draw(Chunk chunk) {
        int w = SIDE + 2 * BORDER;
        double[] height = new double[w * w];
        double[] stone = new double[w * w];
        double x0 = chunk.col * Field.TILE - BORDER / (double) PIXELS;
        double y0 = chunk.row * Field.TILE - BORDER / (double) PIXELS;
        for (int y = 0; y < w; y++) {
            for (int x = 0; x < w; x++) {
                double wx = x0 + x / (double) PIXELS;
                double wy = y0 + y / (double) PIXELS;
                double sx = warpX(wx, wy);
                double sy = warpY(wx, wy);
                double r = Art.smooth(-0.04, 0.04, field(rock, sx, sy) - 0.5);
                double high = Art.smooth(0.35, 0.65, field(cliff, sx, sy));
                double dunes = field(dune, sx, sy);
                double big = Art.fbm(wx / 70.0, wy / 55.0, 3);
                double warp = Art.fbm(wx / 26.0, wy / 26.0, 5) * 22;
                double ripple = Math.sin((wx * 0.55 + wy * 0.85 + warp) * 0.62);
                double sand = big * (9 + dunes * 14) + ripple * 0.32 * (0.5 + big);
                double rocky = 13 + high * 12 + Art.fbm(wx / 11.0, wy / 11.0, 7) * 4
                        + (Art.noise(wx / 3.0, wy / 3.0, 8) - 0.5) * 1.2;
                height[y * w + x] = sand * (1 - r) + rocky * r;
                stone[y * w + x] = r;
            }
        }
        // --8<-- [end:draw]
        int[] ground = new int[SIDE * SIDE];
        int[] crystal = new int[SIDE * SIDE];
        float[] weight = new float[SIDE * SIDE];
        Random random = new Random(chunk.col * 7919L + chunk.row * 104729L + level.number());
        double lx = -0.55;
        double ly = -0.65;
        double lz = 0.52;
        for (int y = 0; y < SIDE; y++) {
            for (int x = 0; x < SIDE; x++) {
                int hx = x + BORDER;
                int hy = y + BORDER;
                int i = hy * w + hx;
                double wx = x0 + hx / (double) PIXELS;
                double wy = y0 + hy / (double) PIXELS;
                double dx = (height[i + 1] - height[i - 1]) * PIXELS / 2;
                double dy = (height[i + w] - height[i - w]) * PIXELS / 2;
                double lit = (-dx * lx - dy * ly + lz) / Math.sqrt(dx * dx + dy * dy + 1) / 0.85;
                // The shadow the cliffs and the dunes cast towards the bottom right.
                double shadow = 1;
                for (int d = 2; d < 18; d += 2) {
                    int sx = hx - d;
                    int sy = hy - (int) (d * 1.15);
                    if (height[sy * w + sx] - d * 0.55 > height[i] + 0.4) {
                        shadow = 0.62;
                        break;
                    }
                }
                double light = (0.5 + 0.62 * Math.max(0, lit)) * shadow;
                double r = stone[i];
                double big = Art.fbm(wx / 160.0, wy / 160.0, 1);
                int col = Art.mix(0xC98E4E, 0xE3B26E, big * 1.4 - 0.2);
                col = Art.mix(col, 0xF2CF92, Art.smooth(0.25, 1, Math.sin((wx * 0.55 + wy * 0.85
                        + Art.fbm(wx / 26.0, wy / 26.0, 5) * 22) * 0.62)) * 0.12);
                col = Art.mix(col, random.nextBoolean() ? 0xA8703C : 0xF4D8A0, random.nextDouble() * 0.12);
                // Scree: darker, gravelly sand at the foot of the rock.
                double scree = Art.smooth(-0.3, -0.02, rock(wx, wy)) * (1 - r);
                col = Art.mix(col, Art.mix(0x8E6A48, 0xB08A60, random.nextDouble()), scree * 0.5);
                if (r > 0) {
                    int rocky = Art.mix(0x6A4C34, 0x9A7652, Art.fbm(wx / 7.0, wy / 7.0, 12));
                    rocky = Art.mix(rocky, 0x4E3828, Art.smooth(0.6, 0.75, Art.fbm(wx / 30.0, wy / 30.0, 14)) * 0.5);
                    if (Math.abs(Art.noise(wx / 9.0, wy / 9.0, 15) - 0.5) < 0.018) rocky = Art.mix(rocky, 0x3A2818, 0.6);
                    rocky = Art.mix(rocky, random.nextBoolean() ? 0x4A3424 : 0xB8946A, random.nextDouble() * 0.16);
                    col = Art.mix(col, rocky, r);
                }
                // Crystal: teal shards, some dark, some glinting.
                int shards = Art.mix(0x2E8478, 0x4CB4A8, Art.fbm(wx / 9.0, wy / 9.0, 9));
                double grain = random.nextDouble();
                if (grain < 0.16) shards = Art.mix(shards, 0x14444A, 0.7);
                else if (grain > 0.95) shards = Art.mix(shards, 0xD8FFF6, 0.8);
                else if (grain > 0.88) shards = Art.mix(shards, 0x8CE8DC, 0.6);
                int o = y * SIDE + x;
                ground[o] = Art.shade(col, light);
                crystal[o] = Art.shade(shards, light);
                weight[o] = (float) ((0.55 + 0.45 * Art.fbm(wx / 20.0, wy / 20.0, 37)) * (1 - r));
            }
        }
        chunk.crystal = crystal;
        chunk.weight = weight;
        chunk.ground = ground;
    }

    // --8<-- [start:mix]
    /** The square's picture with the crystal in {@code steps}: the ground and the crystal's colour mixed, pixel by pixel. */
    private BufferedImage mix(Chunk chunk, byte[] steps) {
        int[] ground = chunk.ground;
        int[] crystal = chunk.crystal;
        float[] weight = chunk.weight;
        float[] amount = new float[(CHUNK + 2) * (CHUNK + 2)];
        for (int i = 0; i < amount.length; i++) amount[i] = steps[i] / (float) STEPS;
        int width = Math.min(SIDE, (level.width() - chunk.col) * Field.TILE * PIXELS);
        int height = Math.min(SIDE, (level.height() - chunk.row) * Field.TILE * PIXELS);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] out = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        double x0 = chunk.col * Field.TILE;
        double y0 = chunk.row * Field.TILE;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * SIDE + x;
                int col = ground[i];
                if (weight[i] > 0) {
                    double wx = x0 + x / (double) PIXELS;
                    double wy = y0 + y / (double) PIXELS;
                    double s = local(amount, warpX(wx, wy) - x0, warpY(wx, wy) - y0);
                    double cover = Art.smooth(0.06, 0.2, s) * (0.75 + 0.25 * Art.smooth(0.2, 0.9, s));
                    if (cover > 0) col = Art.mix(col, crystal[i], cover * weight[i]);
                }
                out[y * width + x] = col;
            }
        }
        pebbles(image, chunk, x0, y0);
        return image;
    }
    // --8<-- [end:mix]

    /** The crystal field inside a square, at {@code x}, {@code y} units from its top left corner. */
    private static double local(float[] amount, double x, double y) {
        double fx = x / Field.TILE + 0.5;
        double fy = y / Field.TILE + 0.5;
        int c = Math.clamp((int) Math.floor(fx), 0, CHUNK);
        int r = Math.clamp((int) Math.floor(fy), 0, CHUNK);
        double tx = Math.clamp(fx - c, 0, 1);
        double ty = Math.clamp(fy - r, 0, 1);
        int n = CHUNK + 2;
        double a = amount[r * n + c];
        double b = amount[r * n + c + 1];
        double d = amount[(r + 1) * n + c];
        double e = amount[(r + 1) * n + c + 1];
        double top = a + (b - a) * tx;
        return top + (d + (e - d) * tx - top) * ty;
    }

    /** Pebbles on the edges of the rock and in the scree, the same ones whenever the square is mixed. */
    private void pebbles(BufferedImage image, Chunk chunk, double x0, double y0) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Random random = new Random(chunk.col * 31L + chunk.row * 1009L + 7);
        Color[] colours = {new Color(0x9A7A58), new Color(0x7A5C40), new Color(0xB89670)};
        Color dark = new Color(0x3E2C1C);
        List<int[]> stones = new ArrayList<>();
        for (int i = 0; i < 160; i++) {
            int x = random.nextInt(SIDE);
            int y = random.nextInt(SIDE);
            int s = 1 + random.nextInt(3);
            int colour = random.nextInt(3);
            double rv = rock(x0 + x / (double) PIXELS, y0 + y / (double) PIXELS);
            if (rv < -0.25 || rv > 0.03) continue;
            stones.add(new int[]{x, y, s, colour});
        }
        for (int[] stone : stones) {
            g.setColor(dark);
            g.fillOval(stone[0] + 1, stone[1] + 1, stone[2], stone[2]);
            g.setColor(colours[stone[3]]);
            g.fillOval(stone[0], stone[1], stone[2], stone[2]);
        }
        g.dispose();
    }
}
