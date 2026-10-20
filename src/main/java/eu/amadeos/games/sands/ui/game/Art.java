package eu.amadeos.games.sands.ui.game;

import eu.amadeos.games.sands.domain.Side;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;

/**
 * Every picture of Sands, drawn in code: the buildings, the vehicles, the carryall, the worm, the
 * explosions. Nothing here is drawn in a frame; {@link Pictures} draws each once into a sprite. Light falls
 * from the top left, so every shadow falls to the bottom right.
 *
 * <p>A building is drawn with the top left corner of its tiles at 0, 0; its shadow reaches beyond them. A
 * vehicle is drawn around 0, 0, facing east, as {@link eu.amadeos.gameengine.sprite.Rotations} turns it.
 * Each side has three shades of its colour, light, middle and dark: {@link #team}.
 */
final class Art {
    /** Where shadows fall, from the light in the top left. */
    static final double LX = 2.0;
    static final double LY = 2.4;
    /** The vehicles are drawn a little larger than the room they take up, as in the classic. */
    static final double U = 1.3;
    /** How far a building's shadow and roof units reach beyond its tiles. */
    static final double MARGIN = 12;

    static final Color[] BLUE = {new Color(0x8FB4F0), new Color(0x3E6FB8), new Color(0x1C355E)};
    static final Color[] RED = {new Color(0xF0907A), new Color(0xB8402E), new Color(0x5A1A10)};

    private Art() {
    }

    static Color[] team(Side side) {
        return side == Side.BLUE ? BLUE : RED;
    }

    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    // ---- noise and colour ------------------------------------------------------------------------

    static double hash(int x, int y, int seed) {
        long h = x * 374761393L + y * 668265263L + seed * 1442695040888963407L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    /** Value noise: smooth hills of 0 to 1, one a unit apart. */
    static double noise(double x, double y, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double fx = x - xi;
        double fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(xi, yi, seed);
        double b = hash(xi + 1, yi, seed);
        double c = hash(xi, yi + 1, seed);
        double d = hash(xi + 1, yi + 1, seed);
        double top = a + (b - a) * fx;
        return top + (c + (d - c) * fx - top) * fy;
    }

    /** Four octaves of noise, large hills with smaller ones on them. */
    static double fbm(double x, double y, int seed) {
        double v = 0;
        double amp = 0.5;
        double f = 1;
        for (int i = 0; i < 4; i++) {
            v += amp * noise(x * f, y * f, seed + i);
            amp *= 0.5;
            f *= 2;
        }
        return v;
    }

    static int mix(int c1, int c2, double t) {
        t = Math.clamp(t, 0, 1);
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    static int shade(int c, double f) {
        int r = (int) Math.min(255, ((c >> 16) & 255) * f);
        int g = (int) Math.min(255, ((c >> 8) & 255) * f);
        int b = (int) Math.min(255, (c & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    static double smooth(double e0, double e1, double x) {
        double t = Math.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static RadialGradientPaint radial(double x, double y, double r, float[] stops, Color... colours) {
        return new RadialGradientPaint(new Point2D.Double(x, y), (float) r, stops, colours);
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    // ---- the parts of buildings ------------------------------------------------------------------

    /** A concrete slab under a building, one tile. */
    static void slab(Graphics2D g, double x, double y, Random random) {
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(0xA49C90), (float) (x + 16), (float) (y + 16),
                new Color(0x857D72)));
        g.fill(new Rectangle2D.Double(x, y, 16, 16));
        g.setColor(new Color(0x5E574E));
        g.setStroke(new BasicStroke(0.6f));
        g.draw(new Rectangle2D.Double(x + 0.3, y + 0.3, 15.4, 15.4));
        g.setColor(new Color(255, 255, 255, 40));
        g.draw(new Line2D.Double(x + 1, y + 1, x + 15, y + 1));
        for (int i = 0; i < 4; i++) {
            g.setColor(new Color(60, 45, 30, 20 + random.nextInt(30)));
            double s = 2 + random.nextDouble() * 5;
            g.fill(new Ellipse2D.Double(x + random.nextDouble() * 12, y + random.nextDouble() * 12, s, s * 0.7));
        }
        g.setColor(new Color(200, 150, 90, 50));
        g.fill(new Rectangle2D.Double(x, y + 13, 16, 3));
    }

    // --8<-- [start:block]
    /** A box seen from above and a little from the south: roof, front wall, and its shadow to the bottom right. */
    static void block(Graphics2D g, double x, double y, double w, double h, double height, int roof, int face) {
        Path2D shadow = new Path2D.Double();
        shadow.moveTo(x + w, y + 2);
        shadow.lineTo(x + w + height * 0.8, y + 2 + height * 0.9);
        shadow.lineTo(x + w + height * 0.8, y + h + height * 0.5);
        shadow.lineTo(x + height * 0.4, y + h + height * 0.5);
        shadow.lineTo(x, y + h);
        shadow.lineTo(x + w, y + h);
        shadow.closePath();
        // The ground darkens right at the walls.
        for (int i = 3; i >= 1; i--) {
            g.setColor(new Color(20, 12, 6, 22));
            g.fill(new RoundRectangle2D.Double(x - i * 0.8, y + h - height * 0.3 - i * 0.6, w + i * 1.6,
                    height * 0.3 + i * 1.6, 3, 3));
        }
        g.setColor(new Color(20, 12, 6, 95));
        g.fill(shadow);
        // --8<-- [end:block]
        Random random = new Random((long) (x * 131 + y * 17));
        double rh = h - height;
        // The front wall: panels, rust and dust running down, sand blown against its foot.
        g.setPaint(new GradientPaint((float) x, (float) (y + rh), new Color(face), (float) x, (float) (y + h),
                new Color(shade(face, 0.6))));
        g.fill(new Rectangle2D.Double(x, y + rh, w, height));
        g.setStroke(new BasicStroke(0.35f));
        for (double px = x + 4; px < x + w - 1; px += 4 + random.nextInt(3)) {
            g.setColor(new Color(0, 0, 0, 55));
            g.draw(new Line2D.Double(px, y + rh + 0.5, px, y + h));
            g.setColor(new Color(255, 255, 255, 25));
            g.draw(new Line2D.Double(px + 0.4, y + rh + 0.5, px + 0.4, y + h));
        }
        for (int i = 0; i < (int) (w / 6); i++) {
            double sx = x + random.nextDouble() * w;
            double length = height * (0.3 + random.nextDouble() * 0.6);
            g.setPaint(new GradientPaint((float) sx, (float) (y + rh), new Color(80, 45, 20, 70), (float) sx,
                    (float) (y + rh + length), new Color(80, 45, 20, 0)));
            g.fill(new Rectangle2D.Double(sx, y + rh, 0.5 + random.nextDouble() * 0.8, length));
        }
        Shape clip = g.getClip();
        g.clip(new Rectangle2D.Double(x - 2, y + rh, w + 4, height + 3));
        for (int i = 0; i < (int) (w / 5); i++) {
            double sx = x - 2 + random.nextDouble() * (w + 4);
            double sw = 4 + random.nextDouble() * 8;
            double sh = 1.5 + random.nextDouble() * 2.5;
            g.setPaint(new GradientPaint(0, (float) (y + h - sh * 0.5), new Color(214, 160, 96, 0), 0, (float) (y + h),
                    new Color(214, 160, 96, 170)));
            g.fill(new Ellipse2D.Double(sx - sw / 2, y + h - sh * 0.5, sw, sh));
        }
        g.setClip(clip);
        // The roof: panels with rivets at their corners, sand blown on from the south west.
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(shade(roof, 1.18)), (float) (x + w),
                (float) (y + rh), new Color(shade(roof, 0.82))));
        g.fill(new Rectangle2D.Double(x, y, w, rh));
        double pw = w > 30 ? w / Math.round(w / 12) : w / 2;
        double ph = rh > 20 ? rh / Math.round(rh / 10) : rh;
        g.setStroke(new BasicStroke(0.35f));
        for (double px = x + pw; px < x + w - 1; px += pw) {
            g.setColor(new Color(0, 0, 0, 45));
            g.draw(new Line2D.Double(px, y + 0.5, px, y + rh));
            g.setColor(new Color(255, 255, 255, 35));
            g.draw(new Line2D.Double(px + 0.4, y + 0.5, px + 0.4, y + rh));
        }
        for (double py = y + ph; py < y + rh - 1; py += ph) {
            g.setColor(new Color(0, 0, 0, 45));
            g.draw(new Line2D.Double(x + 0.5, py, x + w, py));
            g.setColor(new Color(255, 255, 255, 35));
            g.draw(new Line2D.Double(x + 0.5, py + 0.4, x + w, py + 0.4));
        }
        double[][] corners = {{0, 0}, {pw - 3, 0}, {0, ph - 3}, {pw - 3, ph - 3}};
        for (double px = x + 1.5; px < x + w; px += pw) {
            for (double py = y + 1.5; py < y + rh; py += ph) {
                for (double[] o : corners) {
                    if (px + o[0] > x + w - 1 || py + o[1] > y + rh - 1) continue;
                    g.setColor(new Color(0, 0, 0, 70));
                    g.fill(new Ellipse2D.Double(px + o[0] + 0.2, py + o[1] + 0.2, 0.8, 0.8));
                    g.setColor(new Color(255, 255, 255, 90));
                    g.fill(new Ellipse2D.Double(px + o[0], py + o[1], 0.6, 0.6));
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            double dx = x + random.nextDouble() * w * 0.5;
            double dy = y + rh * (0.55 + random.nextDouble() * 0.45);
            double dr = 3 + random.nextDouble() * 6;
            g.setPaint(radial(dx, dy, dr, new float[]{0, 1}, new Color(205, 150, 90, 70), new Color(205, 150, 90, 0)));
            g.fill(new Ellipse2D.Double(dx - dr, dy - dr, dr * 2, dr * 2));
        }
        for (int i = 0; i < (int) (w * rh / 60); i++) {
            g.setColor(new Color(60, 40, 25, 30 + random.nextInt(40)));
            double s = 0.5 + random.nextDouble() * 1.5;
            g.fill(new Ellipse2D.Double(x + random.nextDouble() * (w - 2), y + random.nextDouble() * (rh - 2), s, s * 0.7));
        }
        g.setColor(new Color(255, 255, 255, 80));
        g.setStroke(new BasicStroke(0.6f));
        g.draw(new Line2D.Double(x + 0.3, y + 0.3, x + w - 0.3, y + 0.3));
        g.draw(new Line2D.Double(x + 0.3, y + 0.3, x + 0.3, y + rh));
        g.setColor(new Color(shade(roof, 0.55)));
        g.setStroke(new BasicStroke(0.9f));
        g.draw(new Line2D.Double(x, y + rh - 0.3, x + w, y + rh - 0.3));
        g.setColor(new Color(255, 255, 255, 50));
        g.setStroke(new BasicStroke(0.4f));
        g.draw(new Line2D.Double(x, y + rh + 0.6, x + w, y + rh + 0.6));
    }

    /** A machinery box on a roof, with a fan or with louvres. */
    static void roofUnit(Graphics2D g, double x, double y, double w, double h, boolean fan) {
        g.setColor(new Color(20, 12, 6, 80));
        g.fill(new Rectangle2D.Double(x + 1, y + 1.2, w, h));
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(0xCCC6BA), (float) (x + w), (float) (y + h),
                new Color(0x7A7468)));
        g.fill(new Rectangle2D.Double(x, y, w, h));
        g.setColor(new Color(0x4A463E));
        g.setStroke(new BasicStroke(0.3f));
        g.draw(new Rectangle2D.Double(x, y, w, h));
        if (fan) {
            double r = Math.min(w, h) * 0.38;
            double cx = x + w / 2;
            double cy = y + h / 2;
            g.setColor(new Color(0x2A2622));
            g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            g.setColor(new Color(0x8A8478));
            g.setStroke(new BasicStroke(0.4f));
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + 0.4;
                g.draw(new Line2D.Double(cx, cy, cx + Math.cos(a) * r * 0.9, cy + Math.sin(a) * r * 0.9));
            }
        } else {
            g.setStroke(new BasicStroke(0.3f));
            g.setColor(new Color(0x3A3630));
            for (double i = y + 1; i < y + h - 0.5; i += 1) g.draw(new Line2D.Double(x + 1, i, x + w - 1, i));
        }
    }

    static void pipe(Graphics2D g, double x0, double y0, double x1, double y1, double width, Color c) {
        g.setStroke(new BasicStroke((float) width + 0.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(20, 12, 6, 80));
        g.draw(new Line2D.Double(x0 + 0.8, y0 + 1, x1 + 0.8, y1 + 1));
        g.setColor(c.darker().darker());
        g.draw(new Line2D.Double(x0, y0, x1, y1));
        g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c);
        g.draw(new Line2D.Double(x0, y0, x1, y1));
        g.setStroke(new BasicStroke((float) width * 0.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 110));
        g.draw(new Line2D.Double(x0 - width * 0.2, y0 - width * 0.2, x1 - width * 0.2, y1 - width * 0.2));
    }

    static void light(Graphics2D g, double x, double y, Color c) {
        g.setPaint(radial(x, y, 3, new float[]{0, 1}, alpha(c, 150), alpha(c, 0)));
        g.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(x - 0.6, y - 0.6, 1.2, 1.2));
    }

    static void windows(Graphics2D g, double x, double y, int n, double step, Random random) {
        for (int i = 0; i < n; i++) {
            boolean lit = random.nextDouble() < 0.4;
            g.setColor(new Color(0x1E2630));
            g.fill(new Rectangle2D.Double(x + i * step - 0.3, y - 0.3, step * 0.62 + 0.6, 2.6));
            g.setPaint(new GradientPaint(0, (float) y, lit ? new Color(0xFFE6A0) : new Color(0x6A8AA8), 0,
                    (float) (y + 2), lit ? new Color(0xC89A40) : new Color(0x26384A)));
            g.fill(new Rectangle2D.Double(x + i * step, y, step * 0.62, 2));
        }
    }

    static void stripes(Graphics2D g, double x, double y, double w, double h) {
        Shape clip = g.getClip();
        g.clip(new Rectangle2D.Double(x, y, w, h));
        g.setColor(new Color(0xE0B030));
        g.fill(new Rectangle2D.Double(x, y, w, h));
        g.setColor(new Color(0x2A2218));
        g.setStroke(new BasicStroke(1.4f));
        for (double i = -h; i < w; i += 3.5) g.draw(new Line2D.Double(x + i, y + h, x + i + h, y));
        g.setClip(clip);
    }

    static void band(Graphics2D g, double x, double y, double w, double h, Color[] team) {
        g.setPaint(new GradientPaint((float) x, (float) y, team[0], (float) x, (float) (y + h), team[1]));
        g.fill(new Rectangle2D.Double(x, y, w, h));
    }

    static void vents(Graphics2D g, double x, double y, int n, double w) {
        g.setStroke(new BasicStroke(0.6f));
        for (int i = 0; i < n; i++) {
            g.setColor(new Color(0x2E2A26));
            g.draw(new Line2D.Double(x, y + i * 1.6, x + w, y + i * 1.6));
            g.setColor(new Color(255, 255, 255, 40));
            g.draw(new Line2D.Double(x, y + i * 1.6 + 0.6, x + w, y + i * 1.6 + 0.6));
        }
    }

    // ---- the buildings ---------------------------------------------------------------------------

    /**
     * A whole building of {@code id}, {@code width} × {@code height} tiles, on its concrete: everything but
     * the head of a turret, which turns.
     */
    static void building(Graphics2D g, String id, int width, int height, Color[] team) {
        smooth(g);
        Random random = new Random(id.hashCode());
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (id.equals("refinery") && col == 3) continue;        // the pad is the refinery's own
                if (id.equals("silo")) continue;                        // the tanks stand on the ground
                slab(g, col * 16, row * 16, random);
            }
        }
        switch (id) {
            case "yard" -> yard(g, team);
            case "windtrap" -> windtrap(g, team);
            case "refinery" -> refinery(g, team);
            case "silo" -> silo(g, team);
            case "outpost" -> outpost(g, team);
            case "factory" -> factory(g, team);
            case "turret" -> turretBase(g);
            default -> block(g, 2, 2, width * 16 - 4, height * 16 - 4, 6, 0xA89E8C, 0x7E7466);
        }
    }

    static void yard(Graphics2D g, Color[] team) {
        block(g, 2, 4, 60, 58, 9, 0xB4AA98, 0x8A8070);
        // The landing pad with its hazard rim.
        stripes(g, 8, 10, 30, 30);
        g.setPaint(radial(20, 22, 16, new float[]{0, 1}, new Color(0x7A746A), new Color(0x4A463E)));
        g.fill(new Rectangle2D.Double(10.5, 12.5, 25, 25));
        g.setColor(new Color(0x36322C));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Line2D.Double(23, 12.5, 23, 37.5));
        band(g, 42, 8, 16, 4, team);
        vents(g, 42, 16, 6, 16);
        // A crane: its mast and a long boom over the pad.
        double px = 50;
        double py = 34;
        g.setColor(new Color(20, 12, 6, 80));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(px + 5, py + 6, px - 22, py - 8));
        g.setColor(new Color(0xD8A828));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(px, py, px - 27, py - 14));
        g.setColor(new Color(0x6A5214));
        g.setStroke(new BasicStroke(0.5f));
        for (int i = 1; i < 9; i++) {
            g.draw(new Line2D.Double(px - i * 3, py - i * 1.55 - 1, px - i * 3 - 1.5, py - i * 1.55 + 1));
        }
        g.setPaint(radial(px - 1, py - 1, 4, new float[]{0, 1}, new Color(0xF0D070), new Color(0x8A6818)));
        g.fill(new Ellipse2D.Double(px - 3.5, py - 3.5, 7, 7));
        g.setColor(new Color(0x3A3430));
        g.setStroke(new BasicStroke(0.5f));
        g.draw(new Line2D.Double(px - 27, py - 14, px - 27, py - 8));
        g.fill(new Rectangle2D.Double(px - 28.5, py - 8, 3, 2));
        windows(g, 6, 55, 7, 8, new Random(3));
        roofUnit(g, 8, 43, 7, 6, true);
        roofUnit(g, 17, 43, 7, 6, true);
        roofUnit(g, 27, 44, 9, 4, false);
        pipe(g, 36, 46, 44, 46, 1.2, new Color(0x8A8478));
        pipe(g, 44, 46, 44, 40, 1.2, new Color(0x8A8478));
        // An antenna with a warning light, and the side's flag.
        g.setColor(new Color(20, 12, 6, 90));
        g.setStroke(new BasicStroke(0.7f));
        g.draw(new Line2D.Double(58, 10, 63, 15));
        g.setColor(new Color(0x3A3632));
        g.draw(new Line2D.Double(58, 10, 58, 3));
        light(g, 58, 3, new Color(0xFF4030));
        light(g, 4, 7, new Color(0xFFB040));
        flag(g, 6, 14, team);
    }

    static void windtrap(Graphics2D g, Color[] team) {
        block(g, 2, 6, 28, 24, 6, 0xA89E8C, 0x7E7466);
        vents(g, 5, 10, 5, 8);
        band(g, 5, 20, 22, 2.5, team);
        // The intake tower: a cylinder with a fan on top.
        double cx = 20;
        double cy = 11;
        g.setColor(new Color(20, 12, 6, 90));
        g.fill(new Ellipse2D.Double(cx + 2, cy + 3, 12, 12));
        g.setPaint(new GradientPaint((float) (cx - 7), 0, new Color(0xC8C0B0), (float) (cx + 7), 0, new Color(0x6E665A)));
        g.fill(new Rectangle2D.Double(cx - 7, cy, 14, 8));
        g.fill(new Ellipse2D.Double(cx - 7, cy + 4, 14, 8));
        g.setPaint(radial(cx - 2, cy - 2, 9, new float[]{0, 1}, new Color(0xD8D2C4), new Color(0x8A8274)));
        g.fill(new Ellipse2D.Double(cx - 7, cy - 4, 14, 8));
        g.setColor(new Color(0x2C2824));
        g.fill(new Ellipse2D.Double(cx - 5, cy - 2.8, 10, 5.6));
        g.setColor(new Color(0x8C8478));
        g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5 + 0.3;
            g.draw(new Line2D.Double(cx, cy, cx + Math.cos(a) * 4.5, cy + Math.sin(a) * 2.5));
        }
        g.setColor(new Color(0xB8B0A0));
        g.fill(new Ellipse2D.Double(cx - 1.2, cy - 0.8, 2.4, 1.6));
        // Pipes into the ground, and a tank of water.
        pipe(g, 8, 22, 8, 31, 1.8, new Color(0x7A7468));
        pipe(g, 14, 22, 14, 31, 1.8, new Color(0x7A7468));
        g.setPaint(radial(25, 26, 3, new float[]{0, 1}, new Color(0xA8D0E8), new Color(0x3A6A8A)));
        g.fill(new Ellipse2D.Double(23, 24, 5, 4));
        light(g, 27, 9, new Color(0x60E060));
    }

    /** The outpost: its radar dish turned to the north east. */
    static void outpost(Graphics2D g, Color[] team) {
        block(g, 2, 10, 28, 20, 6, 0xA49A88, 0x7A7062);
        band(g, 4, 12, 24, 2.5, team);
        vents(g, 18, 17, 3, 9);
        double cx = 12;
        double cy = 14;
        g.setColor(new Color(20, 12, 6, 90));
        g.fill(new Ellipse2D.Double(cx - 4, cy + 2, 20, 12));
        g.setColor(new Color(0x5A544A));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Line2D.Double(cx, cy + 6, cx, cy - 2));
        AffineTransform at = g.getTransform();
        g.translate(cx, cy - 4);
        g.rotate(-0.5);
        g.setPaint(radial(-3, -2, 12, new float[]{0, 0.7f, 1}, new Color(0xF2EEE6), new Color(0xB8B2A6),
                new Color(0x6E685E)));
        g.fill(new Ellipse2D.Double(-10, -6, 20, 12));
        g.setColor(new Color(0x5A544A));
        g.setStroke(new BasicStroke(0.6f));
        g.draw(new Ellipse2D.Double(-10, -6, 20, 12));
        g.draw(new Ellipse2D.Double(-5, -3, 10, 6));
        g.draw(new Line2D.Double(0, 0, 6, -6));
        g.setColor(new Color(0xE84030));
        g.fill(new Ellipse2D.Double(5, -7.5, 3, 3));
        g.setTransform(at);
    }

    static void factory(Graphics2D g, Color[] team) {
        block(g, 2, 2, 60, 44, 10, 0xAEA492, 0x847A6A);
        g.setStroke(new BasicStroke(0.6f));
        for (int i = 0; i < 9; i++) {
            g.setColor(new Color(0, 0, 0, 50));
            g.draw(new Line2D.Double(6 + i * 6, 5, 6 + i * 6, 26));
            g.setColor(new Color(255, 255, 255, 50));
            g.draw(new Line2D.Double(6.7 + i * 6, 5, 6.7 + i * 6, 26));
        }
        band(g, 4, 28, 56, 4, team);
        // The big door in the front, in a hazard frame.
        stripes(g, 18, 36, 28, 10);
        g.setPaint(new GradientPaint(0, 37, new Color(0x3A3632), 0, 46, new Color(0x1E1C1A)));
        g.fill(new Rectangle2D.Double(20, 37.5, 24, 8.5));
        g.setColor(new Color(0x56504A));
        g.setStroke(new BasicStroke(0.5f));
        for (int i = 0; i < 4; i++) g.draw(new Line2D.Double(20, 39.5 + i * 2, 44, 39.5 + i * 2));
        for (int i = 0; i < 2; i++) {
            double sx = 8 + i * 44;
            double sy = 10;
            g.setPaint(radial(sx - 1, sy - 1, 4, new float[]{0, 1}, new Color(0x8A847A), new Color(0x3E3A34)));
            g.fill(new Ellipse2D.Double(sx - 3, sy - 3, 6, 6));
            g.setColor(new Color(0x1A1816));
            g.fill(new Ellipse2D.Double(sx - 1.6, sy - 1.6, 3.2, 3.2));
        }
        for (int i = 0; i < 3; i++) {
            double wx = 18 + i * 10;
            g.setPaint(new GradientPaint((float) wx, 8, new Color(0x9ACBE8), (float) (wx + 6), 20, new Color(0x2A4A66)));
            g.fill(new Rectangle2D.Double(wx, 8, 6, 12));
            g.setColor(new Color(0x4A463E));
            g.setStroke(new BasicStroke(0.4f));
            g.draw(new Rectangle2D.Double(wx, 8, 6, 12));
            g.draw(new Line2D.Double(wx, 14, wx + 6, 14));
            g.setColor(new Color(255, 255, 255, 120));
            g.draw(new Line2D.Double(wx + 1, 9, wx + 1, 13));
        }
        roofUnit(g, 6, 16, 7, 7, true);
        roofUnit(g, 50, 16, 7, 7, true);
        pipe(g, 3, 30, 3, 45, 1.4, new Color(0x8A7A60));
        pipe(g, 61, 30, 61, 45, 1.4, new Color(0x8A7A60));
        light(g, 17, 35, new Color(0xFFA030));
        light(g, 47, 35, new Color(0xFFA030));
    }

    static void refinery(Graphics2D g, Color[] team) {
        block(g, 2, 2, 44, 30, 8, 0xA89E8C, 0x7E7466);
        band(g, 4, 20, 40, 2.5, team);
        // Three tanks with spice-stained tops.
        for (int i = 0; i < 3; i++) {
            double cx = 10 + i * 13;
            double cy = 11;
            g.setColor(new Color(20, 12, 6, 80));
            g.fill(new Ellipse2D.Double(cx - 3, cy - 2.5, 10, 10));
            g.setPaint(radial(cx - 2, cy - 2, 6.5, new float[]{0, 0.7f, 1}, new Color(0xE8E0D0), new Color(0xA49C8C),
                    new Color(0x625A4E)));
            g.fill(new Ellipse2D.Double(cx - 5.5, cy - 5.5, 11, 11));
            g.setColor(new Color(200, 100, 30, 130));
            g.fill(new Ellipse2D.Double(cx - 2, cy - 2, 4, 4));
            g.setColor(new Color(0x5A5248));
            g.setStroke(new BasicStroke(0.4f));
            g.draw(new Ellipse2D.Double(cx - 5.5, cy - 5.5, 11, 11));
        }
        pipe(g, 36, 17, 50, 17, 1.6, new Color(0x9A9080));
        pipe(g, 10, 17.5, 36, 17.5, 1.2, new Color(0x9A9080));
        g.setStroke(new BasicStroke(0.3f));
        g.setColor(new Color(0x3A3630));
        for (int i = 0; i < 3; i++) {
            double lx = 13 + i * 13;
            g.draw(new Line2D.Double(lx, 7, lx, 15));
            g.draw(new Line2D.Double(lx + 1.2, 7, lx + 1.2, 15));
            for (double ly = 8; ly < 15; ly += 1.4) g.draw(new Line2D.Double(lx, ly, lx + 1.2, ly));
        }
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(0xE07A2A));
        g.draw(new Line2D.Double(4, 25, 44, 25));
        g.setColor(new Color(255, 160, 60, 70));
        g.setStroke(new BasicStroke(3f));
        g.draw(new Line2D.Double(4, 25, 44, 25));
        // The pad on the east side, where the harvesters unload.
        g.setColor(new Color(20, 12, 6, 70));
        g.fill(new Rectangle2D.Double(47, 4, 18, 26));
        g.setColor(new Color(0x6E685E));
        g.fill(new Rectangle2D.Double(46, 3, 18, 26));
        g.setColor(new Color(0x4E4840));
        g.setStroke(new BasicStroke(0.5f));
        g.draw(new Rectangle2D.Double(46.3, 3.3, 17.4, 25.4));
        g.setColor(new Color(0xE0B030));
        g.setStroke(new BasicStroke(1.1f));
        for (int i = 0; i < 3; i++) {
            Path2D chevron = new Path2D.Double();
            chevron.moveTo(50 + i * 4, 9);
            chevron.lineTo(53 + i * 4, 16);
            chevron.lineTo(50 + i * 4, 23);
            g.draw(chevron);
        }
        light(g, 47, 4, new Color(0xFFB040));
        light(g, 63, 4, new Color(0xFFB040));
        light(g, 63, 28, new Color(0xFFB040));
    }

    static void silo(Graphics2D g, Color[] team) {
        for (int i = 0; i < 4; i++) {
            double cx = 9 + (i % 2) * 14;
            double cy = 9 + (i / 2) * 14;
            g.setColor(new Color(20, 12, 6, 90));
            g.fill(new Ellipse2D.Double(cx - 3, cy - 2.5, 12, 12));
            g.setPaint(radial(cx - 2.5, cy - 2.5, 7.5, new float[]{0, 0.6f, 1}, new Color(0xF0E8D8),
                    new Color(0xB0A694), new Color(0x5E564A)));
            g.fill(new Ellipse2D.Double(cx - 6, cy - 6, 12, 12));
            g.setColor(team[1]);
            g.setStroke(new BasicStroke(0.9f));
            g.draw(new Arc2D.Double(cx - 4, cy - 4, 8, 8, 200, 140, Arc2D.OPEN));
        }
    }

    /** The turret's armoured base; the head turns on it. */
    static void turretBase(Graphics2D g) {
        double cx = 8;
        double cy = 8;
        g.setColor(new Color(20, 12, 6, 100));
        g.fill(new Ellipse2D.Double(cx - 7 + LX, cy - 7 + LY, 14, 14));
        g.setPaint(radial(cx - 2, cy - 2, 9, new float[]{0, 1}, new Color(0xB8B0A2), new Color(0x5C564C)));
        g.fill(new RoundRectangle2D.Double(cx - 7, cy - 7, 14, 14, 5, 5));
        g.setColor(new Color(0x4A443C));
        g.setStroke(new BasicStroke(0.4f));
        g.draw(new RoundRectangle2D.Double(cx - 7, cy - 7, 14, 14, 5, 5));
    }

    /** The turret's head around 0, 0, its gun to the east. */
    static void turretHead(Graphics2D g, Color[] team) {
        smooth(g);
        g.setColor(new Color(20, 12, 6, 80));
        g.fill(new Rectangle2D.Double(2 + 0.8, -1.3 + 1, 10, 2.6));
        g.setColor(new Color(0x34302A));
        g.fill(new Rectangle2D.Double(2, -1.3, 10, 2.6));
        g.setColor(new Color(0x6A645A));
        g.fill(new Rectangle2D.Double(2, -1.3, 10, 1));
        g.setColor(new Color(0x24201C));
        g.fill(new Rectangle2D.Double(11, -1.7, 1.6, 3.4));
        g.setPaint(radial(-1.5, -1.5, 5, new float[]{0, 1}, team[0], team[2]));
        g.fill(new Ellipse2D.Double(-4.5, -4.5, 9, 9));
        g.setColor(new Color(0, 0, 0, 100));
        g.setStroke(new BasicStroke(0.3f));
        g.draw(new Ellipse2D.Double(-4.5, -4.5, 9, 9));
        g.setColor(new Color(255, 255, 255, 120));
        g.fill(new Ellipse2D.Double(-2.6, -2.6, 1.6, 1.6));
    }

    static void flag(Graphics2D g, double x, double y, Color[] team) {
        g.setColor(new Color(0x3A3430));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Line2D.Double(x, y, x, y - 12));
        Path2D f = new Path2D.Double();
        f.moveTo(x, y - 12);
        f.curveTo(x + 4, y - 13, x + 6, y - 10, x + 10, y - 11);
        f.lineTo(x + 10, y - 6);
        f.curveTo(x + 6, y - 5, x + 4, y - 8, x, y - 7);
        f.closePath();
        g.setPaint(new GradientPaint((float) x, 0, team[0], (float) (x + 10), 0, team[1]));
        g.fill(f);
    }

    // ---- the vehicles ----------------------------------------------------------------------------

    /** The size of a vehicle's sprite, the side of a square that holds it at every angle. */
    static double size(String unit) {
        return switch (unit) {
            case "trike" -> 18;
            case "harvester" -> 34;
            case "launcher" -> 26;
            default -> 30;
        };
    }

    /** Length and width of a vehicle's hull, for its shadow. */
    static double[] hull(String unit) {
        return switch (unit) {
            case "trike" -> new double[]{9, 6};
            case "harvester" -> new double[]{21, 13};
            case "launcher" -> new double[]{14, 9};
            default -> new double[]{13, 10};
        };
    }

    /** A vehicle's shadow on the ground, its centre at 0, 0; the sprite is drawn shifted by {@link #LX}, {@link #LY}. */
    static void shadow(Graphics2D g, String unit) {
        smooth(g);
        double[] hull = hull(unit);
        g.scale(U, U);
        g.setColor(new Color(30, 15, 5, 95));
        g.fill(new RoundRectangle2D.Double(-hull[0] / 2 - 0.5, -hull[1] / 2 - 0.5, hull[0] + 1, hull[1] + 1, 4, 4));
    }

    static void rivet(Graphics2D g, double x, double y) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Double(x - 0.2, y - 0.2, 0.7, 0.7));
        g.setColor(new Color(255, 255, 255, 120));
        g.fill(new Ellipse2D.Double(x - 0.35, y - 0.35, 0.45, 0.45));
    }

    /** Two tracks with their links, on either side of the axis. */
    static void treads(Graphics2D g, double l, double w, double off) {
        for (int s = -1; s <= 1; s += 2) {
            double ty = s * off - w / 2;
            g.setColor(new Color(0x1C1814));
            g.fill(new RoundRectangle2D.Double(-l / 2, ty, l, w, w * 0.8, w * 0.8));
            g.setStroke(new BasicStroke(0.35f));
            for (double i = -l / 2 + 0.8; i < l / 2 - 0.4; i += 1.1) {
                g.setColor(new Color(0x6A6256));
                g.draw(new Line2D.Double(i, ty + 0.25, i, ty + w - 0.25));
                g.setColor(new Color(0x2E2A24));
                g.draw(new Line2D.Double(i + 0.45, ty + 0.25, i + 0.45, ty + w - 0.25));
            }
            g.setColor(new Color(255, 240, 210, 40));
            g.fill(new Rectangle2D.Double(-l / 2 + 1, ty + 0.2, l - 2, w * 0.3));
            g.setColor(new Color(200, 150, 90, 60));
            g.fill(new Rectangle2D.Double(-l / 2 + 0.5, ty + w * 0.65, l - 1, w * 0.3));
        }
    }

    // --8<-- [start:tank]
    /** A tank's hull: tracks, a sloped front, the engine deck at the back. Its turret turns on its own. */
    static void tankHull(Graphics2D g, Color[] team) {
        smooth(g);
        g.scale(U, U);
        treads(g, 13, 3, 3.6);
        Path2D hull = new Path2D.Double();
        hull.moveTo(-5.8, -3.6);
        hull.lineTo(4.2, -3.6);
        hull.lineTo(6.2, -2.4);
        hull.lineTo(6.2, 2.4);
        hull.lineTo(4.2, 3.6);
        hull.lineTo(-5.8, 3.6);
        hull.closePath();
        g.setPaint(new GradientPaint(-5, -4, team[0], 5, 4, team[2]));
        g.fill(hull);
        // --8<-- [end:tank]
        Path2D glacis = new Path2D.Double();
        glacis.moveTo(3.6, -3.2);
        glacis.lineTo(6, -2.2);
        glacis.lineTo(6, 2.2);
        glacis.lineTo(3.6, 3.2);
        glacis.closePath();
        g.setColor(new Color(255, 255, 255, 45));
        g.fill(glacis);
        g.setColor(new Color(0, 0, 0, 110));
        g.setStroke(new BasicStroke(0.3f));
        g.draw(hull);
        g.setColor(new Color(0x24201C));
        g.fill(new Rectangle2D.Double(-5.4, -2.2, 2.4, 4.4));
        g.setColor(new Color(0x5A544A));
        g.setStroke(new BasicStroke(0.25f));
        for (double i = -2; i < 2.2; i += 0.7) g.draw(new Line2D.Double(-5.2, i, -3.2, i));
        g.setColor(new Color(0x3A3430));
        g.fill(new Ellipse2D.Double(-6.4, -3.2, 1.2, 1.2));
        g.fill(new Ellipse2D.Double(-6.4, 2, 1.2, 1.2));
        g.setColor(new Color(0xFFF2C0));
        g.fill(new Ellipse2D.Double(5.3, -2.9, 0.9, 0.9));
        g.fill(new Ellipse2D.Double(5.3, 2.0, 0.9, 0.9));
        g.setColor(new Color(0x4A4238));
        g.fill(new Rectangle2D.Double(-2.6, 2.4, 3.2, 1));
        for (double i = -4.8; i < 4.5; i += 1.6) {
            rivet(g, i, -3.25);
            rivet(g, i, 3.25);
        }
    }

    /** A tank's turret around its pivot: an angular cast shape, the barrel with its muzzle brake. */
    static void tankTurret(Graphics2D g, Color[] team) {
        smooth(g);
        g.scale(U, U);
        g.setColor(new Color(20, 12, 6, 80));
        g.fill(new Rectangle2D.Double(2, -0.3, 8, 1.6));
        g.setPaint(new GradientPaint(2, -1, new Color(0x6A645A), 2, 1, new Color(0x24201C)));
        g.fill(new Rectangle2D.Double(2, -0.75, 7.4, 1.5));
        g.setColor(new Color(0x2A2622));
        g.fill(new RoundRectangle2D.Double(9, -1.1, 1.8, 2.2, 0.6, 0.6));
        g.setColor(new Color(0x8A8478));
        g.setStroke(new BasicStroke(0.2f));
        g.draw(new Line2D.Double(9.5, -1, 9.5, 1));
        Path2D t = new Path2D.Double();
        t.moveTo(-3.6, -2.4);
        t.lineTo(1.2, -3.1);
        t.lineTo(3.2, -1.6);
        t.lineTo(3.2, 1.6);
        t.lineTo(1.2, 3.1);
        t.lineTo(-3.6, 2.4);
        t.lineTo(-4.2, 0);
        t.closePath();
        g.setPaint(radial(-1.5, -1.5, 5, new float[]{0, 0.6f, 1}, team[0], team[1], team[2]));
        g.fill(t);
        g.setColor(new Color(0, 0, 0, 120));
        g.setStroke(new BasicStroke(0.3f));
        g.draw(t);
        g.setColor(new Color(255, 255, 255, 70));
        g.draw(new Line2D.Double(-3.4, -2.2, 1.1, -2.9));
        g.setPaint(radial(-1.6, -1.4, 1.6, new float[]{0, 1}, new Color(0x8A8478), new Color(0x2A2622)));
        g.fill(new Ellipse2D.Double(-2.4, -2.1, 1.9, 1.9));
        g.setColor(new Color(0x1A1612));
        g.fill(new Ellipse2D.Double(-1.6, 0.4, 1.4, 1.4));
        g.setColor(new Color(0x2A2622));
        g.setStroke(new BasicStroke(0.2f));
        g.draw(new Line2D.Double(-3.2, 1.6, -7, 3.2));
    }

    /** A whole tank, hull and turret the same way: for the panel. */
    static void tank(Graphics2D g, Color[] team) {
        AffineTransform at = g.getTransform();
        tankHull(g, team);
        g.setTransform(at);
        tankTurret(g, team);
        g.setTransform(at);
    }

    static void trike(Graphics2D g, Color[] team) {
        smooth(g);
        g.scale(U, U);
        for (double[] wheel : new double[][]{{2.6, -1.3, 3, 2.6}, {-4.6, -4.2, 3.6, 2.2}, {-4.6, 2, 3.6, 2.2}}) {
            g.setColor(new Color(0x1C1814));
            g.fill(new RoundRectangle2D.Double(wheel[0], wheel[1], wheel[2], wheel[3], 1.2, 1.2));
            g.setColor(new Color(0x6A6256));
            g.setStroke(new BasicStroke(0.25f));
            for (double i = wheel[0] + 0.5; i < wheel[0] + wheel[2]; i += 0.8) {
                g.draw(new Line2D.Double(i, wheel[1] + 0.2, i, wheel[1] + wheel[3] - 0.2));
            }
        }
        g.setColor(new Color(0x4A4238));
        g.setStroke(new BasicStroke(0.5f));
        g.draw(new Line2D.Double(-3, -3, -3, 3));
        Path2D body = new Path2D.Double();
        body.moveTo(4.2, 0);
        body.lineTo(-0.5, -2.6);
        body.lineTo(-4, -2.2);
        body.lineTo(-4, 2.2);
        body.lineTo(-0.5, 2.6);
        body.closePath();
        g.setPaint(new GradientPaint(-3, -2, team[0], 3, 2, team[2]));
        g.fill(body);
        g.setColor(new Color(0, 0, 0, 110));
        g.setStroke(new BasicStroke(0.3f));
        g.draw(body);
        g.setPaint(radial(-1.8, -0.6, 1.6, new float[]{0, 1}, new Color(0xC8B488), new Color(0x5A4A30)));
        g.fill(new Ellipse2D.Double(-2.6, -1.3, 2.6, 2.6));
        g.setColor(new Color(0x2A2622));
        g.fill(new Rectangle2D.Double(0, -0.4, 5.5, 0.8));
        g.setColor(new Color(0xFFF2C0));
        g.fill(new Ellipse2D.Double(3.4, -1.6, 0.8, 0.8));
    }

    // --8<-- [start:launcher]
    /**
     * The rocket launcher, new in the game: a six-wheeled truck with a box of rockets on its back, tilted up.
     * It has no turret of its own: the whole truck turns to fire.
     */
    static void launcher(Graphics2D g, Color[] team) {
        smooth(g);
        g.scale(U, U);
        for (double wx : new double[]{-4.6, -1.2, 3.4}) {
            for (int s = -1; s <= 1; s += 2) {
                g.setColor(new Color(0x1C1814));
                g.fill(new RoundRectangle2D.Double(wx - 1.3, s * 3.6 - 1.1, 2.6, 2.2, 1, 1));
                g.setColor(new Color(0x5A5248));
                g.setStroke(new BasicStroke(0.25f));
                g.draw(new Line2D.Double(wx - 0.6, s * 3.6 - 1, wx - 0.6, s * 3.6 + 1));
                g.draw(new Line2D.Double(wx + 0.4, s * 3.6 - 1, wx + 0.4, s * 3.6 + 1));
            }
        }
        // --8<-- [end:launcher]
        // The chassis and the cab at the front.
        g.setPaint(new GradientPaint(-6, -3, new Color(0xB0A48A), 6, 3, new Color(0x5E5442)));
        g.fill(new RoundRectangle2D.Double(-6.8, -3, 13, 6, 1.2, 1.2));
        g.setPaint(new GradientPaint(3, -3, team[0], 6.5f, 3, team[2]));
        g.fill(new RoundRectangle2D.Double(3, -3.2, 3.8, 6.4, 1.6, 1.6));
        g.setColor(new Color(0, 0, 0, 110));
        g.setStroke(new BasicStroke(0.3f));
        g.draw(new RoundRectangle2D.Double(3, -3.2, 3.8, 6.4, 1.6, 1.6));
        g.setPaint(new GradientPaint(5.4f, -2.4f, new Color(0x9ACBE8), 6.6f, 2.4f, new Color(0x1E3448)));
        g.fill(new RoundRectangle2D.Double(5.4, -2.4, 1.1, 4.8, 0.6, 0.6));
        // The box of rockets: six tubes, the warheads at the front, the box's shadow on the chassis.
        g.setColor(new Color(20, 12, 6, 90));
        g.fill(new RoundRectangle2D.Double(-6, -2.2, 8.6, 5.6, 1, 1));
        g.setPaint(new GradientPaint(-6.5f, -2.8f, team[1], 2, 2.8f, team[2]));
        g.fill(new RoundRectangle2D.Double(-6.8, -2.8, 8.8, 5.6, 1, 1));
        g.setColor(new Color(0, 0, 0, 120));
        g.draw(new RoundRectangle2D.Double(-6.8, -2.8, 8.8, 5.6, 1, 1));
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 2; j++) {
                double ty = -1.8 + i * 1.8;
                double tx = -1.6 + j * 2.0;
                g.setColor(new Color(0x1A1612));
                g.fill(new Ellipse2D.Double(tx - 0.75, ty - 0.75, 1.5, 1.5));
                g.setColor(new Color(0xE0D8C8));
                g.fill(new Ellipse2D.Double(tx - 0.45, ty - 0.45, 0.9, 0.9));
                g.setColor(new Color(0xE04020));
                g.fill(new Ellipse2D.Double(tx - 0.2, ty - 0.2, 0.4, 0.4));
            }
        }
        g.setColor(new Color(255, 255, 255, 60));
        g.draw(new Line2D.Double(-6.5, -2.6, 1.8, -2.6));
        g.setColor(new Color(0xE0B030));
        g.fill(new Rectangle2D.Double(-6.6, -2.6, 1, 5.2));
        for (double i = -5.5; i < 2; i += 2) {
            rivet(g, i, -3);
            rivet(g, i, 3);
        }
    }

    /** A harvester, its hopper filled to {@code load}, 0 to 1. */
    static void harvester(Graphics2D g, Color[] team, double load) {
        smooth(g);
        g.scale(U, U);
        treads(g, 18, 3.6, 4.6);
        g.setPaint(new GradientPaint(-8, -5, new Color(0xDCCCA4), 8, 5, new Color(0x6E6048)));
        g.fill(new RoundRectangle2D.Double(-9.5, -4.4, 16, 8.8, 2, 2));
        g.setColor(new Color(0, 0, 0, 110));
        g.setStroke(new BasicStroke(0.3f));
        g.draw(new RoundRectangle2D.Double(-9.5, -4.4, 16, 8.8, 2, 2));
        // The hopper: walls, the heap of spice with its grains, the ribs.
        g.setColor(new Color(0x3A3226));
        g.fill(new Rectangle2D.Double(-8, -3.2, 9.4, 6.4));
        if (load > 0) {
            double top = -3.2 + 6.4 * (1 - load);
            g.setPaint(radial(-3.5, -0.5, 5, new float[]{0, 0.6f, 1}, new Color(0xF8A050), new Color(0xD06A28),
                    new Color(0x7A3410)));
            g.fill(new Rectangle2D.Double(-8, top, 9.4, 3.2 - top));
            Random grains = new Random(31);
            for (int i = 0; i < (int) (40 * load); i++) {
                g.setColor(grains.nextBoolean() ? new Color(0x8A3A10) : new Color(0xFFC080));
                g.fill(new Ellipse2D.Double(-7.8 + grains.nextDouble() * 9, top + grains.nextDouble() * (3.1 - top),
                        0.45, 0.45));
            }
        }
        g.setColor(new Color(0x2A241C));
        g.setStroke(new BasicStroke(0.45f));
        for (int i = 0; i < 3; i++) g.draw(new Line2D.Double(-8 + (i + 1) * 2.35, -3.2, -8 + (i + 1) * 2.35, 3.2));
        g.setColor(new Color(255, 255, 255, 60));
        g.setStroke(new BasicStroke(0.3f));
        g.draw(new Line2D.Double(-8, -3.3, 1.4, -3.3));
        for (double i = -9; i < 6; i += 2) {
            rivet(g, i, -4);
            rivet(g, i, 4);
        }
        g.setColor(new Color(0x2A2622));
        g.fill(new Ellipse2D.Double(1.6, -4.1, 1.4, 1.4));
        g.fill(new Ellipse2D.Double(1.6, 2.7, 1.4, 1.4));
        light(g, 1.1, 0, new Color(0xFFA020));
        // The cab in the side's colour, with its windscreen.
        g.setPaint(new GradientPaint(2.2f, -4, team[0], 6.5f, 4, team[2]));
        g.fill(new RoundRectangle2D.Double(2.4, -3.8, 4.4, 7.6, 1.5, 1.5));
        g.setColor(new Color(0, 0, 0, 110));
        g.draw(new RoundRectangle2D.Double(2.4, -3.8, 4.4, 7.6, 1.5, 1.5));
        g.setPaint(new GradientPaint(5, -2.6f, new Color(0x9ACBE8), 6.4f, 2.6f, new Color(0x1E3448)));
        g.fill(new RoundRectangle2D.Double(5, -2.8, 1.5, 5.6, 0.8, 0.8));
        g.setColor(new Color(255, 255, 255, 150));
        g.setStroke(new BasicStroke(0.25f));
        g.draw(new Line2D.Double(5.4, -2.2, 5.4, -0.6));
        // The drum at the front that digs up the spice, with its teeth.
        g.setPaint(new GradientPaint(7, -5.5f, new Color(0x8A8070), 7, 5.5f, new Color(0x2E2A24)));
        g.fill(new RoundRectangle2D.Double(7, -5.8, 2.8, 11.6, 1.6, 1.6));
        g.setColor(new Color(0x1A1612));
        g.setStroke(new BasicStroke(0.35f));
        for (double i = -5.2; i < 5.4; i += 1.1) g.draw(new Line2D.Double(7.2, i, 9.6, i + 0.5));
        g.setColor(new Color(0xB8B0A0));
        for (double i = -5; i < 5.4; i += 1.6) g.draw(new Line2D.Double(9.8, i, 10.7, i + 0.2));
    }

    /** A vehicle by its name, whole: hull and, for a tank, its turret the same way. */
    static void vehicle(Graphics2D g, String unit, Color[] team, double load) {
        switch (unit) {
            case "trike" -> trike(g, team);
            case "launcher" -> launcher(g, team);
            case "harvester" -> harvester(g, team, load);
            default -> tank(g, team);
        }
    }

    static Shape wing() {
        Path2D w = new Path2D.Double();
        w.moveTo(2, -1);
        w.lineTo(-2, -11);
        w.lineTo(-5, -11);
        w.lineTo(-4, -1);
        w.lineTo(-4, 1);
        w.lineTo(-5, 11);
        w.lineTo(-2, 11);
        w.lineTo(2, 1);
        w.closePath();
        return w;
    }

    /** The carryall that flies new harvesters in. */
    static void carryall(Graphics2D g, Color[] team) {
        smooth(g);
        g.setPaint(new GradientPaint(-6, -10, new Color(0xC8C2B6), 6, 10, new Color(0x6A6458)));
        g.fill(wing());
        g.setColor(new Color(0x4A463E));
        g.setStroke(new BasicStroke(0.4f));
        g.draw(wing());
        g.setPaint(new GradientPaint(-8, -2, new Color(0xE0DAD0), 8, 2, new Color(0x8A8478)));
        g.fill(new RoundRectangle2D.Double(-9, -2.2, 18, 4.4, 3, 3));
        g.setColor(team[1]);
        g.fill(new Rectangle2D.Double(-4, -10.5, 3, 2));
        g.fill(new Rectangle2D.Double(-4, 8.5, 3, 2));
        g.setColor(new Color(0x2A3848));
        g.fill(new Ellipse2D.Double(5, -1.4, 3, 2.8));
        g.setColor(new Color(255, 180, 90, 160));
        g.fill(new Ellipse2D.Double(-10, -1, 2, 2));
    }

    static void carryallShadow(Graphics2D g) {
        smooth(g);
        g.setColor(new Color(30, 15, 5, 60));
        g.fill(wing());
    }

    // ---- the worm --------------------------------------------------------------------------------

    // --8<-- [start:worm]
    /**
     * The worm out of the sand, from above: a ring of churned sand, its body's rings, and the round maw
     * with three rings of teeth.
     */
    static void worm(Graphics2D g, double r) {
        smooth(g);
        g.setPaint(radial(0, 0, r, new float[]{0.55f, 0.8f, 1}, new Color(120, 70, 30, 150),
                new Color(200, 140, 80, 90), new Color(200, 140, 80, 0)));
        g.fill(new Ellipse2D.Double(-r, -r, 2 * r, 2 * r));
        double body = r * 0.62;
        g.setColor(new Color(30, 15, 5, 110));
        g.fill(new Ellipse2D.Double(-body + LX, -body + LY, 2 * body, 2 * body));
        g.setPaint(radial(-body * 0.3, -body * 0.3, body * 1.2, new float[]{0, 0.7f, 1}, new Color(0xD8B888),
                new Color(0x8A6440), new Color(0x4A3020)));
        g.fill(new Ellipse2D.Double(-body, -body, 2 * body, 2 * body));
        g.setStroke(new BasicStroke(0.6f));
        for (int i = 1; i <= 3; i++) {
            double ring = body * (1 - i * 0.12);
            g.setColor(new Color(60, 35, 15, 120));
            g.draw(new Ellipse2D.Double(-ring, -ring, 2 * ring, 2 * ring));
        }
        // --8<-- [end:worm]
        double maw = body * 0.58;
        g.setPaint(radial(0, 0, maw, new float[]{0, 0.6f, 1}, new Color(0x0A0402), new Color(0x3A1408),
                new Color(0x8A3A20)));
        g.fill(new Ellipse2D.Double(-maw, -maw, 2 * maw, 2 * maw));
        for (int ring = 0; ring < 3; ring++) {
            double from = maw * (0.95 - ring * 0.26);
            int teeth = 14 - ring * 3;
            for (int i = 0; i < teeth; i++) {
                double a = i * 2 * Math.PI / teeth + ring * 0.3;
                Path2D tooth = new Path2D.Double();
                double side = 0.18;
                tooth.moveTo(Math.cos(a - side) * from, Math.sin(a - side) * from);
                tooth.lineTo(Math.cos(a) * from * 0.72, Math.sin(a) * from * 0.72);
                tooth.lineTo(Math.cos(a + side) * from, Math.sin(a + side) * from);
                tooth.closePath();
                g.setColor(new Color(0xF0E2C0));
                g.fill(tooth);
            }
        }
    }

    /** Where the worm moves under the sand: a bulge of sand with ripples, its head to the east. */
    static void wormSign(Graphics2D g) {
        smooth(g);
        for (int i = 0; i < 3; i++) {
            double r = 6 + i * 4;
            g.setStroke(new BasicStroke(1.4f - i * 0.3f));
            g.setColor(new Color(90, 45, 15, 90 - i * 25));
            g.draw(new Ellipse2D.Double(-r + 0.8, -r * 0.8 + 0.8, r * 2, r * 1.6));
            g.setColor(new Color(255, 225, 170, 80 - i * 22));
            g.draw(new Ellipse2D.Double(-r, -r * 0.8, r * 2, r * 1.6));
        }
        g.setPaint(radial(0, 0, 5, new float[]{0, 1}, new Color(60, 30, 10, 140), new Color(60, 30, 10, 0)));
        g.fill(new Ellipse2D.Double(-5, -4, 10, 8));
    }

    // ---- shots and fire --------------------------------------------------------------------------

    /** A shell: a bright head with a short glowing tail behind it, to the west. */
    static void shell(Graphics2D g) {
        smooth(g);
        g.setPaint(new GradientPaint(-6, 0, new Color(255, 200, 80, 0), 0, 0, new Color(255, 230, 140, 230)));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(-6, 0, 0, 0));
        g.setColor(new Color(255, 250, 220));
        g.fill(new Ellipse2D.Double(-1.2, -1.2, 2.4, 2.4));
    }

    /** A rocket with its flame. */
    static void rocket(Graphics2D g) {
        smooth(g);
        g.setPaint(radial(-4, 0, 4, new float[]{0, 1}, new Color(255, 230, 150, 230), new Color(255, 120, 30, 0)));
        g.fill(new Ellipse2D.Double(-8, -2.5, 7, 5));
        g.setColor(new Color(0xE8E0D0));
        g.fill(new RoundRectangle2D.Double(-2.5, -0.8, 5, 1.6, 1.4, 1.4));
        g.setColor(new Color(0xD03018));
        g.fill(new Ellipse2D.Double(1.6, -0.8, 1.6, 1.6));
    }

    /** The flash at a muzzle, its gun pointing east from 0, 0. */
    static void flash(Graphics2D g) {
        smooth(g);
        g.setPaint(radial(3, 0, 4, new float[]{0, 1}, new Color(255, 240, 180, 230), new Color(255, 160, 40, 0)));
        g.fill(new Ellipse2D.Double(-1, -4, 8, 8));
    }

    /** An explosion of radius {@code r} with its sparks, drawn {@code strength} 1 bright to 0 gone. */
    static void explosion(Graphics2D g, double x, double y, double r, double strength, long seed) {
        if (strength <= 0 || r <= 0) return;
        int a = (int) Math.round(255 * Math.clamp(strength, 0, 1));
        g.setPaint(radial(x, y, r * 1.8, new float[]{0, 1}, new Color(255, 160, 60, 90 * a / 255),
                new Color(255, 120, 30, 0)));
        g.fill(new Ellipse2D.Double(x - r * 1.8, y - r * 1.8, r * 3.6, r * 3.6));
        g.setPaint(radial(x - r * 0.15, y - r * 0.15, r, new float[]{0, 0.25f, 0.6f, 1}, new Color(255, 255, 235, a),
                new Color(255, 230, 120, a), new Color(240, 120, 30, 220 * a / 255), new Color(160, 40, 10, 0)));
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        Random random = new Random(seed);
        g.setStroke(new BasicStroke(0.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 220, 120, 200 * a / 255));
        for (int i = 0; i < 9; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double d0 = r * 0.6;
            double d1 = r * (1.1 + random.nextDouble() * 0.7);
            g.draw(new Line2D.Double(x + Math.cos(angle) * d0, y + Math.sin(angle) * d0, x + Math.cos(angle) * d1,
                    y + Math.sin(angle) * d1));
        }
    }

    /** A puff of smoke around 0, 0, of radius {@code r}. */
    static void puff(Graphics2D g, double r, int a) {
        smooth(g);
        g.setPaint(radial(-r * 0.3, -r * 0.3, r, new float[]{0, 1}, new Color(110, 100, 92, a), new Color(50, 44, 40, 0)));
        g.fill(new Ellipse2D.Double(-r, -r, r * 2, r * 2));
    }

    /** What is left of a vehicle: a burnt hull on scorched sand, around 0, 0. */
    static void wreck(Graphics2D g, double size) {
        smooth(g);
        double k = size / 13;
        g.setColor(new Color(20, 10, 5, 90));
        g.fill(new Ellipse2D.Double(-12 * k, -8 * k, 24 * k, 16 * k));
        g.rotate(0.5);
        g.setColor(new Color(0x2A2420));
        g.fill(new RoundRectangle2D.Double(-7 * k, -5 * k, 14 * k, 10 * k, 3, 3));
        g.setColor(new Color(0x463A30));
        g.fill(new Ellipse2D.Double(-3 * k, -3 * k, 6 * k, 6 * k));
        g.setColor(new Color(0x1A1410));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new Line2D.Double(k, k, 8 * k, 5 * k));
    }

    /** A burnt-out building's ground, {@code w} × {@code h}. */
    static void rubble(Graphics2D g, double w, double h, long seed) {
        smooth(g);
        Random random = new Random(seed);
        g.setColor(new Color(20, 10, 5, 110));
        g.fill(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 6, 6));
        for (int i = 0; i < (int) (w * h / 18); i++) {
            double s = 1 + random.nextDouble() * 3;
            int grey = 40 + random.nextInt(60);
            g.setColor(new Color(grey, grey - 8, grey - 16));
            g.fill(new Rectangle2D.Double(2 + random.nextDouble() * (w - 4 - s), 2 + random.nextDouble() * (h - 4 - s),
                    s, s * (0.5 + random.nextDouble() * 0.5)));
        }
    }

    // ---- marks -----------------------------------------------------------------------------------

    /** The four corners around a chosen thing, {@code s} wide, in white with a shadow. */
    static void brackets(Graphics2D g, double x, double y, double s) {
        double h = s / 2;
        double k = Math.min(3.5, s / 4);
        g.setStroke(new BasicStroke(1f));
        double[][] corners = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int pass = 0; pass < 2; pass++) {
            g.setColor(pass == 0 ? new Color(0, 0, 0, 120) : Color.WHITE);
            double o = pass == 0 ? 0.6 : 0;
            for (double[] c : corners) {
                double cx = x + c[0] * h + o;
                double cy = y + c[1] * h + o;
                g.draw(new Line2D.Double(cx, cy, cx - c[0] * k, cy));
                g.draw(new Line2D.Double(cx, cy, cx, cy - c[1] * k));
            }
        }
    }

    static Color health(double part) {
        return part > 0.6 ? new Color(0x5CD84A) : part > 0.3 ? new Color(0xE8C83A) : new Color(0xE84A3A);
    }

    /** A bar of health {@code width} wide, its middle at {@code x}, its top at {@code y}. */
    static void healthBar(Graphics2D g, double x, double y, double width, double part) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fill(new Rectangle2D.Double(x - width / 2 - 0.5, y - 0.5, width + 1, 3));
        g.setColor(health(part));
        g.fill(new Rectangle2D.Double(x - width / 2, y, width * Math.clamp(part, 0, 1), 2));
    }

    /** Where a group was sent: four arrows pointing at the spot. */
    static void target(Graphics2D g, double x, double y, Color colour) {
        for (int pass = 0; pass < 2; pass++) {
            g.setColor(pass == 0 ? new Color(0, 0, 0, 160) : colour);
            double o = pass == 0 ? 0.7 : 0;
            for (int i = 0; i < 4; i++) {
                AffineTransform at = g.getTransform();
                g.translate(x + o, y + o);
                g.rotate(i * Math.PI / 2);
                Path2D p = new Path2D.Double();
                p.moveTo(3, 0);
                p.lineTo(7, -2.5);
                p.lineTo(7, 2.5);
                p.closePath();
                g.fill(p);
                g.setTransform(at);
            }
        }
    }

    /** A grain of spice for the credits: an orange ball with darker spots. */
    static void spiceGrain(Graphics2D g, double x, double y, double r) {
        smooth(g);
        g.setPaint(radial(x - r * 0.2, y - r * 0.3, r * 1.2, new float[]{0, 1}, new Color(0xF8B060), new Color(0xA04A14)));
        g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        g.setColor(new Color(0x6A2A08));
        g.fill(new Ellipse2D.Double(x - r * 0.2, y - r * 0.1, r * 0.4, r * 0.4));
        g.fill(new Ellipse2D.Double(x + r * 0.4, y + r * 0.3, r * 0.3, r * 0.3));
    }
}
