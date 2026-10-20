package eu.amadeos.games.sands.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.economy.Production;
import eu.amadeos.gameengine.fog.FogOfWar;
import eu.amadeos.gameengine.minimap.Minimap;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.games.sands.domain.Building;
import eu.amadeos.games.sands.domain.BuildingType;
import eu.amadeos.games.sands.domain.Field;
import eu.amadeos.games.sands.domain.Game;
import eu.amadeos.games.sands.domain.Ghost;
import eu.amadeos.games.sands.domain.Ground;
import eu.amadeos.games.sands.domain.House;
import eu.amadeos.games.sands.domain.Side;
import eu.amadeos.games.sands.domain.Unit;
import eu.amadeos.games.sands.domain.UnitType;
import eu.amadeos.games.sands.repository.Progress;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The overlay: the panel on the right with the credits, the minimap, the power, the build list, the chosen
 * vehicles and the buttons; the messages over the map; and the screens of the title, the choice of a
 * mission, the pause and the end. The canvas draws it after the world, at the full size of the screen, so
 * the letters are sharp in every render mode.
 */
final class Hud {
    static final Color CHALK = new Color(0xF2E8D4);
    static final Color SPICE = new Color(0xF8C878);
    static final Color LABEL = new Color(0xC8A870);
    static final Color GREEN = new Color(0x7AF070);
    static final Color RED = new Color(0xFF6B5B);
    static final Color GREY = new Color(0x8A7A60);
    static final Color BRASS = new Color(0x8A6A3A);
    static final Color DARK = new Color(0x14100C);
    static final Color VEIL = new Color(0, 0, 0, 150);
    static final Color SHADOW = new Color(0, 0, 0, 150);
    static final Color VIEW = new Color(255, 255, 255, 220);
    static final double MESSAGE_SECONDS = 4;
    static final double LEFT = Field.VIEW_WIDTH;
    /** The parts of the panel, in logical coordinates. */
    static final Rectangle2D.Double MINIMAP = new Rectangle2D.Double(LEFT + 8, 30, 112, 90);
    static final Rectangle2D.Double BUILDINGS_TAB = new Rectangle2D.Double(LEFT + 8, 136, 55, 12);
    static final Rectangle2D.Double VEHICLES_TAB = new Rectangle2D.Double(LEFT + 65, 136, 55, 12);
    static final double GRID_TOP = 151;
    static final double CELL_WIDTH = 55;
    static final double CELL_HEIGHT = 48;
    static final int CELLS = 6;
    static final Rectangle2D.Double CHOSEN = new Rectangle2D.Double(LEFT + 8, 304, 112, 60);
    static final Rectangle2D.Double STOP = new Rectangle2D.Double(LEFT + 8, 369, 55, 12);
    static final Rectangle2D.Double SELL = new Rectangle2D.Double(LEFT + 65, 369, 55, 12);
    static final Rectangle2D.Double MENU = new Rectangle2D.Double(LEFT + 8, 384, 112, 12);
    static final double CARD_WIDTH = 176;
    static final double CARD_HEIGHT = 150;
    static final double CARD_GAP = 16;
    static final double CARD_TOP = 110;

    private final Messages msg;
    private final Pictures pictures;
    private final Map<Integer, Font> fonts = new HashMap<>();
    private final Rectangle2D.Double rectangle = new Rectangle2D.Double();
    private final Random grain = new Random(6);
    private final float[] grainY = new float[300];
    private final int[] grainAlpha = new int[300];
    /** What stays the same from frame to frame, drawn once: the panel, the faces of the tabs, buttons and cells. */
    private final Map<String, Sprite> faces = new HashMap<>();

    Hud(Messages msg, Pictures pictures) {
        this.msg = msg;
        this.pictures = pictures;
        for (int i = 0; i < grainY.length; i++) {
            grainY[i] = (float) (grain.nextDouble() * Field.HEIGHT);
            grainAlpha[i] = grain.nextInt(10);
        }
    }

    /** What the panel shows that the game does not know: which list, the chosen ones, where the mouse is. */
    record View(boolean vehicles, List<Unit> chosen, Building building, int group, double mouseX, double mouseY) {
    }

    // --8<-- [start:panel]
    /** The panel on the right: credits, minimap, power, the build list, the chosen and the buttons. */
    void panel(Graphics2D g, Game game, Side side, Minimap minimap, Camera camera, View view, double alpha) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        House house = game.house(side);
        background(g);
        credits(g, house);
        minimap(g, game, side, minimap, camera, alpha);
        power(g, house);
        tab(g, BUILDINGS_TAB, msg.get("panel.buildings"), !view.vehicles(), BUILDINGS_TAB.contains(view.mouseX(), view.mouseY()));
        tab(g, VEHICLES_TAB, msg.get("panel.vehicles"), view.vehicles(), VEHICLES_TAB.contains(view.mouseX(), view.mouseY()));
        List<?> items = view.vehicles() ? vehicles(game, side) : buildings(game, side);
        for (int i = 0; i < CELLS; i++) {
            Rectangle2D.Double cell = cell(i);
            if (i >= items.size()) {
                empty(g, cell);
                continue;
            }
            boolean hover = cell.contains(view.mouseX(), view.mouseY());
            if (items.get(i) instanceof BuildingType type) item(g, cell, type.id(), type.cost(), side, house.yard(), type, hover);
            else if (items.get(i) instanceof UnitType type) item(g, cell, type.id(), type.cost(), side, house.factory(), type, hover);
        }
        chosen(g, view, side);
        boolean sell = view.building() != null && view.building().side() == side && view.building().type().buildable();
        button(g, STOP, msg.get("panel.stop"), !view.chosen().isEmpty(), STOP.contains(view.mouseX(), view.mouseY()));
        button(g, SELL, msg.get("panel.sell"), sell, sell && SELL.contains(view.mouseX(), view.mouseY()));
        button(g, MENU, msg.get("panel.menu"), true, MENU.contains(view.mouseX(), view.mouseY()));
    }
    // --8<-- [end:panel]

    /** The buildings a side may build now, in the order of the rules. */
    static List<BuildingType> buildings(Game game, Side side) {
        return game.rules().buildings().stream().filter(type -> game.available(side, type)).limit(CELLS).toList();
    }

    static List<UnitType> vehicles(Game game, Side side) {
        return game.rules().units().stream().filter(type -> game.available(side, type)).limit(CELLS).toList();
    }

    static Rectangle2D.Double cell(int i) {
        return new Rectangle2D.Double(LEFT + 8 + (i % 2) * 57, GRID_TOP + (i / 2) * 50, CELL_WIDTH, CELL_HEIGHT);
    }

    /** The cell of the build list under a point, or -1. */
    static int cellAt(double x, double y) {
        for (int i = 0; i < CELLS; i++) if (cell(i).contains(x, y)) return i;
        return -1;
    }

    /**
     * A part of the panel drawn once and then only copied: its gradients cost more than the rest of the
     * frame together. {@code drawing} draws at {@code x}, {@code y} as if on the screen.
     */
    private void face(Graphics2D g, String key, double x, double y, double w, double h, Sprite.Drawing drawing) {
        faces.computeIfAbsent(key, k -> Sprite.drawn(w, h, (sg, sw, sh) -> {
            sg.translate(-x, -y);
            drawing.draw(sg, sw, sh);
        })).draw(g, x, y);
    }

    private void background(Graphics2D g) {
        face(g, "panel", LEFT, 0, Field.PANEL, Field.HEIGHT, (sg, w, h) -> paintBackground(sg));
    }

    private void paintBackground(Graphics2D g) {
        g.setPaint(new GradientPaint((float) LEFT, 0, new Color(0x3A3128), (float) Field.WIDTH, 0, new Color(0x241E18)));
        rectangle.setRect(LEFT, 0, Field.PANEL, Field.HEIGHT);
        g.fill(rectangle);
        g.setStroke(new BasicStroke(0.4f));
        for (int i = 0; i < grainY.length; i++) {
            g.setColor(new Color(255, 230, 190, grainAlpha[i]));
            g.draw(new Line2D.Double(LEFT, grainY[i], Field.WIDTH, grainY[i]));
        }
        g.setColor(BRASS);
        rectangle.setRect(LEFT, 0, 1, Field.HEIGHT);
        g.fill(rectangle);
        g.setColor(new Color(0x120E0A));
        rectangle.setRect(LEFT + 1, 0, 1, Field.HEIGHT);
        g.fill(rectangle);
    }

    private void credits(Graphics2D g, House house) {
        g.setColor(DARK);
        g.fill(new RoundRectangle2D.Double(LEFT + 8, 6, Field.PANEL - 16, 18, 4, 4));
        bevel(g, LEFT + 8, 6, Field.PANEL - 16, 18, true);
        pictures.grain.draw(g, LEFT + 13, 10);
        g.setFont(font(12, true));
        g.setColor(house.credits() >= house.storage() && house.storage() > 0 ? RED : SPICE);
        String credits = String.format("%,d", house.credits()).replace(',', ' ');
        g.drawString(credits, (float) (LEFT + Field.PANEL - 14 - g.getFontMetrics().stringWidth(credits)), 19.5f);
    }

    // --8<-- [start:minimap]
    /**
     * The map, small: what the side has seen, its buildings and vehicles and the enemy's it sees, and the
     * view. Only with an outpost that has power; without, a word on what is missing.
     */
    private void minimap(Graphics2D g, Game game, Side side, Minimap minimap, Camera camera, double alpha) {
        g.setColor(new Color(0x050302));
        g.fill(MINIMAP);
        House house = game.house(side);
        if (!game.radar(side)) {
            g.setFont(font(9, true));
            g.setColor(GREY);
            centred(g, msg.get("panel.radar"), MINIMAP.getCenterX(), MINIMAP.getCenterY() - 3);
            g.setFont(font(7, false));
            centred(g, msg.get(house.lowPower() ? "panel.radar.power" : "panel.radar.none"), MINIMAP.getCenterX(),
                    MINIMAP.getCenterY() + 9);
            bevel(g, MINIMAP.x - 1, MINIMAP.y - 1, MINIMAP.width + 2, MINIMAP.height + 2, true);
            return;
        }
        minimap.render(g);
        for (Ghost ghost : house.known()) {
            BuildingType type = ghost.type();
            minimap.box(g, ghost.col() * Field.TILE, ghost.row() * Field.TILE, type.width() * Field.TILE,
                    type.height() * Field.TILE, minimapColour(ghost.side(), side));
        }
        for (Building building : game.buildings()) {
            if (building.side() != side && !game.visible(side, building)) continue;
            minimap.box(g, building.col() * Field.TILE, building.row() * Field.TILE,
                    building.type().width() * Field.TILE, building.type().height() * Field.TILE,
                    minimapColour(building.side(), side));
        }
        for (Unit unit : game.units()) {
            if (unit.side() != side && !game.visible(side, unit)) continue;
            minimap.dot(g, unit.x(alpha), unit.y(alpha), 1.6, unit.side() == side ? Color.WHITE : new Color(0xF04A3A));
        }
        minimap.view(g, camera, alpha, VIEW);
        bevel(g, MINIMAP.x - 1, MINIMAP.y - 1, MINIMAP.width + 2, MINIMAP.height + 2, true);
    }
    // --8<-- [end:minimap]

    private static Color minimapColour(Side owner, Side viewer) {
        return owner == viewer ? Art.team(owner)[0] : new Color(0xF04A3A);
    }

    /** The colour of a tile on the minimap: its ground and spice, dim where nobody sees now, black where nobody has been. */
    static int minimapPixel(Game game, FogOfWar fog, int col, int row) {
        FogOfWar.State state = fog == null ? FogOfWar.State.VISIBLE : fog.state(col, row);
        if (state == FogOfWar.State.UNSEEN) return 0x050302;
        Ground ground = game.level().ground(col, row);
        int colour = switch (ground) {
            case ROCK -> 0x7A5A40;
            case CLIFF -> 0x5A4030;
            case DUNE -> 0xE0AE6A;
            default -> 0xD09A58;
        };
        int spice = game.spice(col, row);
        if (spice > 0) colour = Art.mix(0xC86A2C, 0xA04A18, spice / (double) Ground.THICK.spice());
        return state == FogOfWar.State.SEEN ? Art.shade(colour, 0.55) : colour;
    }

    private void power(Graphics2D g, House house) {
        double y = 125;
        g.setFont(font(7, true));
        g.setColor(LABEL);
        g.drawString(msg.get("panel.power"), (float) LEFT + 8, (float) y + 6.5f);
        double x = LEFT + 44;
        double w = Field.PANEL - 52;
        g.setColor(DARK);
        rectangle.setRect(x, y, w, 8);
        g.fill(rectangle);
        int made = house.powerMade();
        int used = house.powerUsed();
        double full = Math.max(Math.max(made, used), 1) * 1.15;
        boolean low = house.lowPower();
        g.setPaint(low ? new GradientPaint((float) x, 0, new Color(0xA03A20), (float) (x + w), 0, new Color(0xF07040))
                : new GradientPaint((float) x, 0, new Color(0x3AA040), (float) (x + w), 0, new Color(0x8CE070)));
        rectangle.setRect(x + 1, y + 1, (w - 2) * made / full, 6);
        g.fill(rectangle);
        g.setColor(CHALK);
        rectangle.setRect(x + 1 + (w - 2) * used / full - 0.6, y - 1.5, 1.2, 11);
        g.fill(rectangle);
    }

    private void tab(Graphics2D g, Rectangle2D.Double box, String label, boolean active, boolean hover) {
        face(g, "tab " + box.x + active + hover, box.x, box.y, box.width, box.height, (sg, w, h) -> {
            sg.setPaint(new GradientPaint((float) box.x, (float) box.y, active ? new Color(0x6A5A40)
                    : hover ? new Color(0x4A3E30) : new Color(0x2E261E), (float) box.x, (float) (box.y + box.height),
                    active ? new Color(0x463A28) : new Color(0x1E1812)));
            sg.fill(box);
            bevel(sg, box.x, box.y, box.width, box.height, !active);
        });
        g.setFont(font(7, true));
        g.setColor(active ? CHALK : GREY);
        centred(g, label, box.getCenterX(), box.y + 8.5);
    }

    private void empty(Graphics2D g, Rectangle2D.Double cell) {
        g.setColor(new Color(0x1E1914));
        g.fill(cell);
        bevel(g, cell.x, cell.y, cell.width, cell.height, true);
    }

    // --8<-- [start:item]
    /** A thing to build: its picture, name and price; how far it has come, how many more are ordered, or that it is ready. */
    private <T> void item(Graphics2D g, Rectangle2D.Double cell, String id, int cost, Side side, Production<T> line,
                          T item, boolean hover) {
        face(g, "cell " + cell.x + " " + cell.y + " " + id + side + hover, cell.x, cell.y, cell.width, cell.height,
                (sg, w, h) -> {
                    sg.setColor(new Color(0x1A1612));
                    sg.fill(cell);
                    sg.clip(new Rectangle2D.Double(cell.x + 1, cell.y + 1, cell.width - 2, cell.height - 2));
                    sg.setPaint(new GradientPaint((float) cell.x, (float) cell.y, hover ? new Color(0xD8A060)
                            : new Color(0xC08A50), (float) (cell.x + cell.width), (float) (cell.y + cell.height),
                            new Color(0x8A5A30)));
                    sg.fill(cell);
                    pictures.icon(id, side).draw(sg, cell.x + 1, cell.y + 1);
                    sg.setColor(new Color(0, 0, 0, 150));
                    sg.fill(new Rectangle2D.Double(cell.x, cell.y + cell.height - 11, cell.width, 11));
                    sg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    sg.setFont(font(7, true));
                    sg.setColor(CHALK);
                    sg.drawString(msg.get("thing." + id), (float) cell.x + 3, (float) (cell.y + cell.height - 3));
                    sg.setColor(SPICE);
                    String price = Integer.toString(cost);
                    sg.drawString(price, (float) (cell.x + cell.width - 3 - sg.getFontMetrics().stringWidth(price)),
                            (float) (cell.y + cell.height - 3));
                });
        Shape clip = g.getClip();
        g.clip(new Rectangle2D.Double(cell.x + 1, cell.y + 1, cell.width - 2, cell.height - 2));
        boolean ready = item.equals(line.ready());
        if (item.equals(line.current()) && !ready) {
            double done = line.progress();
            g.setColor(new Color(0, 0, 0, 110));
            g.fill(new Arc2D.Double(cell.x - 20, cell.y - 26, cell.width + 40, cell.height + 40, 90,
                    -360 * (1 - done), Arc2D.PIE));
            g.setFont(font(11, true));
            String percent = (int) Math.floor(done * 100) + "%";
            g.setColor(new Color(0, 0, 0, 160));
            centred(g, percent, cell.getCenterX() + 1, cell.y + 25);
            g.setColor(Color.WHITE);
            centred(g, percent, cell.getCenterX(), cell.y + 24);
        }
        int more = line.count(item) - (item.equals(line.current()) && !ready ? 1 : 0);
        if (more > 0) {
            g.setFont(font(8, true));
            g.setColor(new Color(0, 0, 0, 170));
            g.fill(new RoundRectangle2D.Double(cell.x + cell.width - 13, cell.y + 2, 11, 10, 4, 4));
            g.setColor(CHALK);
            centred(g, Integer.toString(more), cell.x + cell.width - 7.5, cell.y + 10);
        }
        if (ready) {
            g.setFont(font(9, true));
            g.setColor(new Color(0, 0, 0, 160));
            centred(g, msg.get("panel.ready"), cell.getCenterX() + 0.6, cell.y + 12.6);
            g.setColor(GREEN);
            centred(g, msg.get("panel.ready"), cell.getCenterX(), cell.y + 12);
        }
        g.setClip(clip);
        bevel(g, cell.x, cell.y, cell.width, cell.height, false);
        if (ready) {
            g.setColor(GREEN);
            g.setStroke(new BasicStroke(1f));
            g.draw(new Rectangle2D.Double(cell.x + 0.5, cell.y + 0.5, cell.width - 1, cell.height - 1));
        }
    }
    // --8<-- [end:item]

    /** The chosen vehicles, up to ten with their health; or the building chosen. */
    private void chosen(Graphics2D g, View view, Side side) {
        g.setColor(DARK);
        g.fill(CHOSEN);
        bevel(g, CHOSEN.x, CHOSEN.y, CHOSEN.width, CHOSEN.height, true);
        double x = CHOSEN.x + 5;
        double y = CHOSEN.y + 11;
        g.setFont(font(8, true));
        g.setColor(CHALK);
        if (view.building() != null) {
            Building building = view.building();
            g.drawString(msg.get("thing." + building.type().id()), (float) x, (float) y);
            pictures.icon(building.type().id(), building.side()).draw(g, x - 2, y + 4);
            Art.healthBar(g, CHOSEN.getCenterX(), CHOSEN.y + CHOSEN.height - 7, CHOSEN.width - 12,
                    building.health() / building.maxHealth());
            return;
        }
        List<Unit> chosen = view.chosen();
        if (chosen.isEmpty()) {
            g.setFont(font(7, false));
            g.setColor(GREY);
            g.drawString(msg.get("panel.none"), (float) x, (float) y);
            return;
        }
        String kind = chosen.stream().map(u -> u.type().id()).distinct().count() == 1
                ? msg.get("thing." + chosen.getFirst().type().id()) : msg.get("panel.vehicles");
        g.drawString(chosen.size() == 1 ? kind : msg.format("panel.chosen", chosen.size(), kind), (float) x, (float) y);
        if (view.group() > 0) {
            g.setFont(font(7, false));
            g.setColor(new Color(0xA89878));
            String group = msg.format("panel.group", view.group());
            g.drawString(group, (float) (CHOSEN.x + CHOSEN.width - 5 - g.getFontMetrics().stringWidth(group)), (float) y);
        }
        for (int i = 0; i < Math.min(10, chosen.size()); i++) {
            Unit unit = chosen.get(i);
            double ux = CHOSEN.x + 4 + (i % 5) * 21.5;
            double uy = CHOSEN.y + 15 + (i / 5) * 23;
            g.setColor(new Color(0x2A241E));
            rectangle.setRect(ux, uy, 19, 18);
            g.fill(rectangle);
            pictures.small(unit.type().id(), side).draw(g, ux + 1, uy + 0.5);
            g.setColor(new Color(0x0A0806));
            rectangle.setRect(ux, uy + 19, 19, 3);
            g.fill(rectangle);
            g.setColor(Art.health(unit.health() / unit.maxHealth()));
            rectangle.setRect(ux + 0.5, uy + 19.5, 18 * unit.health() / unit.maxHealth(), 2);
            g.fill(rectangle);
        }
    }

    private void button(Graphics2D g, Rectangle2D.Double box, String text, boolean enabled, boolean hover) {
        face(g, "button " + box.x + " " + box.y + hover, box.x, box.y, box.width, box.height, (sg, w, h) -> {
            sg.setPaint(new GradientPaint((float) box.x, (float) box.y, hover ? new Color(0x6A5A44) : new Color(0x5A4C3A),
                    (float) box.x, (float) (box.y + box.height), new Color(0x3A3026)));
            sg.fill(box);
            bevel(sg, box.x, box.y, box.width, box.height, false);
        });
        g.setFont(font(7, true));
        g.setColor(enabled ? CHALK : GREY);
        centred(g, text, box.getCenterX(), box.y + 9);
    }

    static void bevel(Graphics2D g, double x, double y, double w, double h, boolean sunk) {
        g.setStroke(new BasicStroke(1f));
        g.setColor(sunk ? DARK : new Color(0x6A5C4A));
        g.draw(new Line2D.Double(x + 0.5, y + 0.5, x + w - 0.5, y + 0.5));
        g.draw(new Line2D.Double(x + 0.5, y + 0.5, x + 0.5, y + h - 0.5));
        g.setColor(sunk ? new Color(0x6A5C4A) : DARK);
        g.draw(new Line2D.Double(x + 0.5, y + h - 0.5, x + w - 0.5, y + h - 0.5));
        g.draw(new Line2D.Double(x + w - 0.5, y + 0.5, x + w - 0.5, y + h - 0.5));
    }

    // ---- over the map ----------------------------------------------------------------------------

    /** The newest message at the bottom of the map, fading out in its last second. */
    void message(Graphics2D g, String line, double shown, Color colour) {
        if (line == null || shown >= MESSAGE_SECONDS) return;
        int a = (int) Math.round(255 * Math.clamp(MESSAGE_SECONDS - shown, 0, 1));
        g.setFont(font(9, true));
        g.setColor(new Color(0, 0, 0, a * 160 / 255));
        g.drawString(line, 9, (float) Field.HEIGHT - 8);
        g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), a));
        g.drawString(line, 8, (float) Field.HEIGHT - 9);
    }

    /** A line at the top left of the map, such as the clock or what F3 shows. */
    void corner(Graphics2D g, String line, double y) {
        g.setFont(font(8, true));
        g.setColor(SHADOW);
        g.drawString(line, 9, (float) y + 1);
        g.setColor(CHALK);
        g.drawString(line, 8, (float) y);
    }

    // ---- the screens -----------------------------------------------------------------------------

    // --8<-- [start:card]
    /** One mission on the choice screen: its number and name, the fastest win, or a lock. */
    void card(Graphics2D g, double x, double y, int number, boolean open, boolean chosen, Progress progress) {
        RoundRectangle2D.Double card = new RoundRectangle2D.Double(x, y, CARD_WIDTH, CARD_HEIGHT, 10, 10);
        g.setPaint(new GradientPaint((float) x, (float) y, chosen ? new Color(0x5A4630) : new Color(0x3A3128), (float) x,
                (float) (y + CARD_HEIGHT), new Color(0x1E1812)));
        g.fill(card);
        g.setColor(chosen ? SPICE : BRASS);
        g.setStroke(new BasicStroke(chosen ? 1.6f : 1f));
        g.draw(card);
        double cx = x + CARD_WIDTH / 2;
        g.setFont(font(34, true));
        g.setColor(open ? SPICE : GREY);
        centred(g, Integer.toString(number), cx, y + 46);
        g.setFont(font(15, true));
        g.setColor(open ? CHALK : GREY);
        centred(g, name(number), cx, y + 72);
        g.setFont(font(10, false));
        if (!open) {
            lock(g, cx, y + 92);
            g.setColor(GREY);
            centred(g, msg.get("select.locked"), cx, y + 134);
            return;
        }
        g.setColor(GREY);
        centred(g, msg.get("mission." + number + ".text"), cx, y + 98);
        int best = progress.best(number);
        g.setColor(best > 0 ? CHALK : GREY);
        centred(g, best > 0 ? msg.format("select.best", clock(best)) : msg.get("select.none"), cx, y + 134);
    }
    // --8<-- [end:card]

    static double cardX(int index, int count) {
        double left = (Field.WIDTH - count * CARD_WIDTH - (count - 1) * CARD_GAP) / 2;
        return left + index * (CARD_WIDTH + CARD_GAP);
    }

    private void lock(Graphics2D g, double cx, double y) {
        g.setColor(GREY);
        g.setStroke(new BasicStroke(3));
        g.draw(new RoundRectangle2D.Double(cx - 7, y - 2, 14, 16, 12, 12));
        rectangle.setRect(cx - 11, y + 7, 22, 16);
        g.fill(rectangle);
        g.setStroke(new BasicStroke(1));
    }

    /** The end of a mission: won or lost, how long it took, and what each side did. */
    void result(Graphics2D g, Game game, Side side, boolean best, boolean last) {
        veil(g);
        boolean won = game.winner() == side;
        text(g, msg.get(won ? "won.title" : "lost.title"), 110, 32, true, won ? SPICE : RED);
        text(g, msg.format("result.time", clock((int) game.time())), 145, 14, false, CHALK);
        if (won && best) text(g, msg.get("won.best"), 165, 12, true, GREEN);
        House mine = game.house(side);
        House theirs = game.house(side.enemy());
        double top = 196;
        g.setFont(font(11, true));
        g.setColor(LABEL);
        centred(g, msg.get("result.you"), Field.WIDTH / 2 + 50, top);
        centred(g, msg.get("result.enemy"), Field.WIDTH / 2 + 130, top);
        String[] rows = {"result.harvested", "result.made", "result.destroyed", "result.lost"};
        int[][] values = {{mine.harvested(), theirs.harvested()}, {mine.made(), theirs.made()},
                {mine.destroyed(), theirs.destroyed()}, {mine.lost(), theirs.lost()}};
        for (int i = 0; i < rows.length; i++) {
            double y = top + 20 + i * 18;
            g.setFont(font(11, false));
            g.setColor(CHALK);
            String label = msg.get(rows[i]);
            g.drawString(label, (float) (Field.WIDTH / 2 - 10 - g.getFontMetrics().stringWidth(label)), (float) y);
            g.setFont(font(11, true));
            centred(g, Integer.toString(values[i][0]), Field.WIDTH / 2 + 50, y);
            centred(g, Integer.toString(values[i][1]), Field.WIDTH / 2 + 130, y);
        }
        text(g, msg.get(won ? (last ? "won.last" : "won.next") : "lost.again"), 340, 13, false, CHALK);
    }

    /** Minutes and seconds. */
    static String clock(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    String name(int number) {
        return msg.get("mission." + number);
    }

    static void centred(Graphics2D g, String text, double x, double y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (x - fm.stringWidth(text) / 2.0), (float) y);
    }

    /** Darkens the screen, or {@code width} of it from the left, so text over it stays readable. */
    void veil(Graphics2D g) {
        veil(g, Field.WIDTH);
    }

    void veil(Graphics2D g, double width) {
        g.setColor(VEIL);
        rectangle.setRect(0, 0, width, Field.HEIGHT);
        g.fill(rectangle);
    }

    /** A line of text centred on the screen, its baseline at {@code y}, with a shadow. */
    void text(Graphics2D g, String line, double y, int size, boolean bold, Color colour) {
        text(g, line, Field.WIDTH / 2, y, size, bold, colour);
    }

    void text(Graphics2D g, String line, double x, double y, int size, boolean bold, Color colour) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font(size, bold));
        float left = (float) (x - g.getFontMetrics().stringWidth(line) / 2.0);
        g.setColor(SHADOW);
        g.drawString(line, left + 1, (float) y + 1);
        g.setColor(colour);
        g.drawString(line, left, (float) y);
    }

    Font font(int size, boolean bold) {
        return fonts.computeIfAbsent(2 * size + (bold ? 1 : 0),
                key -> new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
    }
}
