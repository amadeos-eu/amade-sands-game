package eu.amadeos.games.sands.ui.game;

import eu.amadeos.gameengine.ai.InfluenceMap;
import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.fog.FogOfWar;
import eu.amadeos.gameengine.fog.FogPainter;
import eu.amadeos.gameengine.select.BoxSelect;
import eu.amadeos.gameengine.sprite.Rotations;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.sands.domain.Building;
import eu.amadeos.games.sands.domain.BuildingType;
import eu.amadeos.games.sands.domain.Carryall;
import eu.amadeos.games.sands.domain.Commander;
import eu.amadeos.games.sands.domain.Field;
import eu.amadeos.games.sands.domain.Game;
import eu.amadeos.games.sands.domain.Ghost;
import eu.amadeos.games.sands.domain.Rules;
import eu.amadeos.games.sands.domain.Shot;
import eu.amadeos.games.sands.domain.Side;
import eu.amadeos.games.sands.domain.Task;
import eu.amadeos.games.sands.domain.Unit;
import eu.amadeos.games.sands.domain.Weapon;
import eu.amadeos.games.sands.domain.Worm;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.List;

/**
 * Draws a battle as one side sees it, from the ground up: the desert, tracks and wrecks, the buildings, the
 * vehicles with their shadows, the worm, the shots, the explosions and smoke, the carryalls high above, the
 * fog of war, and over the fog the marks of the player's orders. Nearly all of it is a copy of a sprite.
 */
final class SandsPainter {
    /**
     * What the player is doing, for the marks on the map; all of it may be {@code null} or empty. {@code box}
     * is in world units.
     */
    record Marks(Collection<Unit> chosen, Building building, BuildingType placing, Cell place, Game.Placement result,
                 BoxSelect.Box box, Cell order, double orderAge, Commander influence) {
        static final Marks NONE = new Marks(List.of(), null, null, null, null, null, null, 0, null);
    }

    static final double FLASH_SECONDS = 0.07;
    static final double ORDER_SECONDS = 0.8;
    /** How far a carryall's shadow falls from it: it flies high. */
    static final double FLIGHT_X = 14;
    static final double FLIGHT_Y = 18;
    static final Color OK = new Color(120, 240, 110, 90);
    static final Color BAD = new Color(240, 80, 60, 110);
    static final Color ORDER = new Color(150, 255, 140, 220);
    static final Color ATTACK = new Color(255, 120, 90, 230);
    static final Color BOX = new Color(255, 255, 255, 200);
    static final Color BOX_FILL = new Color(255, 255, 255, 30);

    private final Pictures pictures;
    private final FogPainter fogPainter = new FogPainter(Field.TILE);
    private final Rectangle2D.Double rectangle = new Rectangle2D.Double();
    private final BasicStroke dots = new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1,
            new float[]{0.1f, 4.5f}, 0);
    private final BasicStroke thin = new BasicStroke(0.8f);

    SandsPainter(Rules rules) {
        this.pictures = new Pictures(rules);
    }

    Pictures pictures() {
        return pictures;
    }

    // --8<-- [start:paint]
    /** The battle as {@code viewer} sees it, or all of it for {@code null}, as on the title screen. */
    void paint(Graphics2D g, Game game, Side viewer, Terrain terrain, Camera camera, Effects effects, Marks marks,
               double alpha) {
        Shape clip = g.getClip();
        AffineTransform screen = g.getTransform();
        g.clip(new Rectangle2D.Double(camera.screenLeft(), camera.screenTop(), camera.viewWidth(), camera.viewHeight()));
        g.translate(camera.screenLeft() - camera.x(alpha), camera.screenTop() - camera.y(alpha));
        int fromCol = (int) Math.floor(camera.x(alpha) / Field.TILE) - 1;
        int fromRow = (int) Math.floor(camera.y(alpha) / Field.TILE) - 1;
        int toCol = (int) ((camera.x(alpha) + camera.viewWidth()) / Field.TILE) + 1;
        int toRow = (int) ((camera.y(alpha) + camera.viewHeight()) / Field.TILE) + 1;

        terrain.render(g, camera, alpha);
        effects.renderGround(g, camera, alpha);
        Worm worm = game.worm();
        if (worm != null && !worm.up() && sees(game, viewer, worm.x(alpha), worm.y(alpha))) {
            effects.renderWorm(g, worm, alpha);
        }
        if (viewer != null) ghosts(g, game, viewer, camera, alpha);
        for (Building building : game.buildings()) {
            if (shows(game, viewer, building)) building(g, building, camera, alpha);
        }
        for (Unit unit : game.units()) {
            if (!shows(game, viewer, unit)) continue;
            Rotations shadow = pictures.shadow(unit.type());
            shadow.draw(g, unit.x(alpha) + Art.LX, unit.y(alpha) + Art.LY, unit.angle(alpha));
        }
        for (Unit unit : game.units()) if (shows(game, viewer, unit)) unit(g, unit, alpha);
        if (worm != null && worm.up() && sees(game, viewer, worm.x(alpha), worm.y(alpha))) {
            effects.renderWorm(g, worm, alpha);
        }
        for (Shot shot : game.shots()) {
            if (sees(game, viewer, shot.x(alpha), shot.y(alpha))) shot(g, shot, alpha);
        }
        effects.render(g, alpha);
        for (Carryall carryall : game.carryalls()) carryall(g, carryall, alpha);
        if (viewer != null) fogPainter.render(g, game.house(viewer).fog(), fromCol, fromRow, toCol, toRow);
        if (marks.influence() != null) influence(g, marks.influence(), fromCol, fromRow, toCol, toRow);
        marks(g, marks, viewer, alpha);
        g.setTransform(screen);
        g.setClip(clip);
    }
    // --8<-- [end:paint]

    /** Whether a side sees a point of the world now; everybody does on the title. */
    private static boolean sees(Game game, Side viewer, double x, double y) {
        if (viewer == null) return true;
        FogOfWar fog = game.house(viewer).fog();
        return fog.visible((int) Math.floor(x / Field.TILE), (int) Math.floor(y / Field.TILE));
    }

    private static boolean shows(Game game, Side viewer, Building building) {
        return viewer == null || building.side() == viewer || game.visible(viewer, building);
    }

    private static boolean shows(Game game, Side viewer, Unit unit) {
        return viewer == null || unit.side() == viewer || game.visible(viewer, unit);
    }

    /** The enemy's buildings as the side last saw them, where it does not see now. */
    private void ghosts(Graphics2D g, Game game, Side viewer, Camera camera, double alpha) {
        for (Ghost ghost : game.house(viewer).known()) {
            if (game.thing(ghost.id()) instanceof Building building && game.visible(viewer, building)) continue;
            BuildingType type = ghost.type();
            double x = ghost.col() * Field.TILE;
            double y = ghost.row() * Field.TILE;
            if (!camera.sees(x, y, type.width() * Field.TILE + Art.MARGIN, type.height() * Field.TILE + Art.MARGIN, alpha)) {
                continue;
            }
            pictures.building(type, ghost.side()).draw(g, x, y);
            if (type.weapon() != null) pictures.head(ghost.side()).draw(g, x + 8, y + 8, Math.PI / 2);
        }
    }

    // --8<-- [start:building]
    /** A building, and a turret's head turned towards its target, flashing when it fires. */
    private void building(Graphics2D g, Building building, Camera camera, double alpha) {
        BuildingType type = building.type();
        double x = building.col() * Field.TILE;
        double y = building.row() * Field.TILE;
        if (!camera.sees(x, y, type.width() * Field.TILE + Art.MARGIN, type.height() * Field.TILE + Art.MARGIN, alpha)) {
            return;
        }
        pictures.building(type, building.side()).draw(g, x, y);
        if (type.weapon() == null) return;
        double angle = building.turret(alpha);
        double cx = x + type.width() * Field.TILE / 2.0;
        double cy = y + type.height() * Field.TILE / 2.0;
        pictures.head(building.side()).draw(g, cx, cy, angle);
        if (building.sinceShot() < FLASH_SECONDS) {
            pictures.flash.draw(g, cx + Math.cos(angle) * 12, cy + Math.sin(angle) * 12, angle);
        }
    }
    // --8<-- [end:building]

    // --8<-- [start:unit]
    /** A vehicle: its body, a tank's turret on it, the flash at the muzzle. */
    private void unit(Graphics2D g, Unit unit, double alpha) {
        double x = unit.x(alpha);
        double y = unit.y(alpha);
        double angle = unit.angle(alpha);
        pictures.vehicle(unit.type(), unit.side(), unit.fill()).draw(g, x, y, angle);
        double gun = angle;
        if (unit.type().turret()) {
            gun = unit.turret(alpha);
            pictures.turret(unit.side()).draw(g, x, y, gun);
        }
        Weapon weapon = unit.type().weapon();
        if (weapon != null && weapon.kind() == Weapon.Kind.GUN && unit.sinceShot() < FLASH_SECONDS) {
            double muzzle = muzzle(unit.type().id());
            pictures.flash.draw(g, x + Math.cos(gun) * muzzle, y + Math.sin(gun) * muzzle, gun);
        }
    }
    // --8<-- [end:unit]

    /** How far a vehicle's muzzle is from its middle. */
    static double muzzle(String unit) {
        return switch (unit) {
            case "trike" -> 7.5;
            case "tank" -> 14;
            default -> 8;
        };
    }

    private void shot(Graphics2D g, Shot shot, double alpha) {
        Rotations picture = shot.kind() == Weapon.Kind.ROCKET ? pictures.rocket : pictures.shell;
        picture.draw(g, shot.x(alpha), shot.y(alpha), shot.angle());
    }

    /** A carryall high in the air, its shadow far below it, and the harvester it carries. */
    private void carryall(Graphics2D g, Carryall carryall, double alpha) {
        double x = carryall.x(alpha);
        double y = carryall.y(alpha);
        double angle = carryall.angle();
        pictures.carryallShadow.draw(g, x + FLIGHT_X, y + FLIGHT_Y, angle);
        if (carryall.cargo() != null) {
            pictures.shadow(carryall.cargo()).draw(g, x + FLIGHT_X, y + FLIGHT_Y, angle);
            pictures.vehicle(carryall.cargo(), carryall.side(), 0).draw(g, x, y, angle);
        }
        pictures.carryall(carryall.side()).draw(g, x, y, angle);
    }

    // --8<-- [start:influence]
    /** F3: the enemy's strength as the computer last saw it, red where it is strong. */
    private void influence(Graphics2D g, Commander commander, int fromCol, int fromRow, int toCol, int toRow) {
        InfluenceMap map = commander.influence();
        if (map == null) return;
        double strongest = 0;
        for (int row = 0; row < map.height(); row++) {
            for (int col = 0; col < map.width(); col++) strongest = Math.max(strongest, map.at(col, row));
        }
        if (strongest <= 0) return;
        for (int row = Math.max(0, fromRow); row <= Math.min(map.height() - 1, toRow); row++) {
            for (int col = Math.max(0, fromCol); col <= Math.min(map.width() - 1, toCol); col++) {
                // The square root, so that a small army far from the strongest place still shows.
                double part = Math.sqrt(map.at(col, row) / strongest);
                if (part <= 0.1) continue;
                g.setColor(new Color(255, (int) Math.round(200 * (1 - part)), 30, (int) Math.round(60 + 130 * part)));
                rectangle.setRect(col * Field.TILE, row * Field.TILE, Field.TILE, Field.TILE);
                g.fill(rectangle);
            }
        }
    }
    // --8<-- [end:influence]

    /** The orders of the chosen vehicles, their corners and health, the building to place, the box being dragged. */
    private void marks(Graphics2D g, Marks marks, Side viewer, double alpha) {
        for (Unit unit : marks.chosen()) {
            if (!unit.alive()) continue;
            Cell goal = unit.goal();
            boolean going = goal != null && (unit.task() == Task.MOVE || unit.task() == Task.HUNT);
            if (!going) continue;
            g.setStroke(dots);
            g.setColor(unit.task() == Task.HUNT ? ATTACK : ORDER);
            g.draw(new Line2D.Double(unit.x(alpha), unit.y(alpha), (goal.col() + 0.5) * Field.TILE,
                    (goal.row() + 0.5) * Field.TILE));
        }
        if (marks.order() != null && marks.orderAge() < ORDER_SECONDS) {
            double grow = 1 + marks.orderAge() / ORDER_SECONDS;
            AffineTransform at = g.getTransform();
            double x = (marks.order().col() + 0.5) * Field.TILE;
            double y = (marks.order().row() + 0.5) * Field.TILE;
            g.translate(x, y);
            g.scale(grow, grow);
            Art.target(g, 0, 0, ORDER);
            g.setTransform(at);
        }
        for (Unit unit : marks.chosen()) {
            if (!unit.alive()) continue;
            double size = Math.max(10, unit.radius() * 2 * Art.U + 6);
            double x = unit.x(alpha);
            double y = unit.y(alpha);
            Art.brackets(g, x, y, size);
            Art.healthBar(g, x, y - size / 2 - 4.5, size - 2, unit.health() / unit.maxHealth());
        }
        Building building = marks.building();
        if (building != null && building.health() > 0) {
            double w = building.type().width() * Field.TILE;
            double h = building.type().height() * Field.TILE;
            double x = building.col() * Field.TILE;
            double y = building.row() * Field.TILE;
            Art.brackets(g, x + w / 2, y + h / 2, Math.max(w, h) + 2);
            Art.healthBar(g, x + w / 2, y - 5, w - 4, building.health() / building.maxHealth());
        }
        if (marks.placing() != null && marks.place() != null) placing(g, marks.placing(), marks.place(), marks.result(), viewer);
        BoxSelect.Box box = marks.box();
        if (box != null) {
            rectangle.setRect(box.left(), box.top(), box.right() - box.left(), box.bottom() - box.top());
            g.setColor(BOX_FILL);
            g.fill(rectangle);
            g.setColor(BOX);
            g.setStroke(thin);
            g.draw(rectangle);
        }
    }

    // --8<-- [start:placing]
    /** The building the yard has ready, where the mouse would put it: each tile green where it may stand, else red. */
    private void placing(Graphics2D g, BuildingType type, Cell at, Game.Placement result, Side side) {
        Composite before = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        Sprite picture = pictures.building(type, side == null ? Side.BLUE : side);
        picture.draw(g, at.col() * Field.TILE, at.row() * Field.TILE);
        g.setComposite(before);
        g.setColor(result == Game.Placement.OK ? OK : BAD);
        for (int dy = 0; dy < type.height(); dy++) {
            for (int dx = 0; dx < type.width(); dx++) {
                rectangle.setRect((at.col() + dx) * Field.TILE + 0.5, (at.row() + dy) * Field.TILE + 0.5,
                        Field.TILE - 1, Field.TILE - 1);
                g.fill(rectangle);
            }
        }
    }
    // --8<-- [end:placing]
}
