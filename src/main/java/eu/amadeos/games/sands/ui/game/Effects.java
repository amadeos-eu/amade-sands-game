package eu.amadeos.games.sands.ui.game;

import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.fx.Particles;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.games.sands.domain.Building;
import eu.amadeos.games.sands.domain.BuildingType;
import eu.amadeos.games.sands.domain.Event;
import eu.amadeos.games.sands.domain.Field;
import eu.amadeos.games.sands.domain.Game;
import eu.amadeos.games.sands.domain.Side;
import eu.amadeos.games.sands.domain.Unit;
import eu.amadeos.games.sands.domain.UnitType;
import eu.amadeos.games.sands.domain.Weapon;
import eu.amadeos.games.sands.domain.Burrower;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the events of a step look and sound like: muzzle flashes and explosions, smoke that drifts with the
 * wind, wrecks and the rubble of buildings, the tracks the vehicles leave in the sand, crystal dust behind a
 * harvester at work, and the sounds. The rules know nothing of this. Everything lives in world units and is
 * drawn by the painter.
 *
 * <p>Sounds are only heard of what the player can see, or of what happens to the player's own side.
 */
final class Effects {
    /** Sparks, dust, crystal, fire, metal. */
    static final Color[] PALETTE = {new Color(0xFFE6A0), new Color(0xC89A64), new Color(0x58C0B0),
            new Color(0xFF8A2A), new Color(0x4A4038)};
    static final int SPARK = 0;
    static final int DUST = 1;
    static final int CRYSTAL = 2;
    static final int FIRE = 3;
    static final int METAL = 4;
    static final double BLAST_SECONDS = 0.45;
    static final double PUFF_SECONDS = 2.2;
    static final double WRECK_SECONDS = 45;
    static final double RUBBLE_SECONDS = 90;
    static final double TRACK_SECONDS = 25;
    static final double TEXT_SECONDS = 1.4;
    /** A vehicle leaves a mark in the sand every this many units it drives. */
    static final double TRACK_STEP = 3;
    static final int MOST_TRACKS = 2400;
    static final int MOST_PUFFS = 240;
    static final double SOUND_GAP = 0.06;
    /** Smoke drifts with the wind, to the north east, in units per second. */
    static final double WIND_X = 6;
    static final double WIND_Y = -9;

    private record Blast(double x, double y, int size, double born) {
    }

    private record Puff(double x, double y, int size, double born, double life) {
    }

    private record Wreck(double x, double y, UnitType type, double born) {
    }

    private record Rubble(double x, double y, BuildingType type, double born) {
    }

    /** Two marks in the sand side by side, as a vehicle's tracks or wheels leave them. */
    private record Track(double x, double y, double dx, double dy, double born) {
    }

    private record Text(double x, double y, String text, Color colour, double born) {
    }

    private final SoundBank sounds = new SoundBank();
    private final Music music = new Music();
    private final Mixer mixer;
    private final Pictures pictures;
    private final SeededRandom random = new SeededRandom(1);
    private final Particles particles = new Particles(1200, PALETTE);
    private final List<Blast> blasts = new ArrayList<>();
    private final ArrayDeque<Puff> puffs = new ArrayDeque<>();
    private final List<Wreck> wrecks = new ArrayList<>();
    private final List<Rubble> rubble = new ArrayList<>();
    private final ArrayDeque<Track> tracks = new ArrayDeque<>();
    private final List<Text> texts = new ArrayList<>();
    /** Where each vehicle left its last mark. */
    private final Map<Integer, double[]> lastMark = new HashMap<>();
    private final Map<Sound, Double> played = new HashMap<>();
    private final Font font = new Font(Font.SANS_SERIF, Font.BOLD, 8);
    private final Rectangle2D.Double rectangle = new Rectangle2D.Double();
    private final Ellipse2D.Double ellipse = new Ellipse2D.Double();
    private final Color[] trackShades = new Color[4];
    private double time;
    private double smoke;

    Effects(Mixer mixer, Pictures pictures) {
        this.mixer = mixer;
        this.pictures = pictures;
        for (int i = 0; i < trackShades.length; i++) trackShades[i] = new Color(90, 50, 20, 70 - i * 16);
    }

    double time() {
        return time;
    }

    /** A new mission, or the title: nothing left from before, no music. */
    void clear() {
        particles.clear();
        blasts.clear();
        puffs.clear();
        wrecks.clear();
        rubble.clear();
        tracks.clear();
        texts.clear();
        lastMark.clear();
        mixer.stopMusic();
    }

    void music() {
        mixer.playMusic(music.desert, true);
    }

    void tick() {
        play(sounds.tick);
    }

    void no() {
        play(sounds.no);
    }

    void yes() {
        play(sounds.yes);
    }

    // --8<-- [start:update]
    /** Time goes on: what has burnt out goes, damaged things smoke, vehicles leave tracks, harvesters throw up dust. */
    void update(double seconds, Game game) {
        time += seconds;
        particles.update(seconds, 0);
        blasts.removeIf(blast -> time - blast.born() > BLAST_SECONDS);
        while (!puffs.isEmpty() && time - puffs.peekFirst().born() > PUFF_SECONDS) puffs.removeFirst();
        wrecks.removeIf(wreck -> time - wreck.born() > WRECK_SECONDS);
        rubble.removeIf(r -> time - r.born() > RUBBLE_SECONDS);
        while (!tracks.isEmpty() && time - tracks.peekFirst().born() > TRACK_SECONDS) tracks.removeFirst();
        texts.removeIf(text -> time - text.born() > TEXT_SECONDS);
        if (game == null) return;
        smoke += seconds;
        boolean puff = smoke >= 0.25;
        if (puff) smoke = 0;
        for (Unit unit : game.units()) {
            track(game, unit);
            if (unit.harvesting() && random.nextDouble() < seconds * 30) crystalDust(unit);
            if (puff && unit.health() < unit.maxHealth() * 0.35) puff(unit.x(), unit.y() - 2, 1);
        }
        lastMark.keySet().removeIf(id -> game.thing(id) == null);
        if (puff) {
            for (Building building : game.buildings()) {
                double part = building.health() / building.maxHealth();
                if (part < 0.5 && random.nextDouble() < 1.2 - part * 2) {
                    double x = (building.col() + random.nextDouble() * building.type().width()) * Field.TILE;
                    double y = (building.row() + random.nextDouble() * building.type().height() * 0.6) * Field.TILE;
                    puff(x, y, part < 0.25 ? 3 : 2);
                    if (part < 0.25) sparkle(x, y, 2, FIRE, 12);
                }
            }
        }
    }
    // --8<-- [end:update]

    /** A mark in the sand behind a vehicle every few units; none on rock. */
    private void track(Game game, Unit unit) {
        double[] last = lastMark.get(unit.id());
        double x = unit.x();
        double y = unit.y();
        if (last == null) {
            lastMark.put(unit.id(), new double[]{x, y});
            return;
        }
        double dx = x - last[0];
        double dy = y - last[1];
        double d = Math.hypot(dx, dy);
        if (d < TRACK_STEP) return;
        last[0] = x;
        last[1] = y;
        int col = (int) Math.floor(x / Field.TILE);
        int row = (int) Math.floor(y / Field.TILE);
        if (!game.contains(col, row) || !game.level().ground(col, row).sandy() || d > 4 * TRACK_STEP) return;
        double half = unit.radius() * Art.U * 0.85;
        tracks.addLast(new Track(x, y, -dy / d * half, dx / d * half, time));
        if (tracks.size() > MOST_TRACKS) tracks.removeFirst();
    }

    private void crystalDust(Unit unit) {
        double a = unit.angle() + Math.PI + (random.nextDouble() - 0.5) * 1.2;
        double v = 8 + random.nextDouble() * 14;
        double front = unit.radius() * 1.6;
        double x = unit.x() + Math.cos(unit.angle()) * front;
        double y = unit.y() + Math.sin(unit.angle()) * front;
        particles.spawn(x, y, Math.cos(a) * v + WIND_X, Math.sin(a) * v + WIND_Y, 0.6 + random.nextDouble() * 0.6,
                1.2 + random.nextDouble() * 2, random.nextDouble() < 0.7 ? CRYSTAL : DUST);
    }

    // --8<-- [start:react]
    /** Shows what happened in the last step, and plays it if {@code audible}, as far as {@code viewer} can see it. */
    void react(Game game, Side viewer, boolean audible) {
        for (Event event : game.events()) {
            switch (event) {
                case Event.Fired(double x, double y, double angle, Weapon.Kind kind, Side side) -> {
                    if (kind == Weapon.Kind.ROCKET) {
                        puff(x - Math.cos(angle) * 4, y - Math.sin(angle) * 4, 1);
                        if (heard(game, viewer, audible, x, y, side)) play(sounds.rocket);
                    } else if (heard(game, viewer, audible, x, y, side)) {
                        Unit shooter = game.unitAt(x, y);
                        play(shooter != null && shooter.type().turret() || shooter == null ? sounds.cannon : sounds.gun);
                    }
                }
                case Event.Hit(double x, double y) -> {
                    blast(x, y, 0);
                    sparkle(x, y, 4, SPARK, 40);
                    if (heard(game, viewer, audible, x, y, null)) play(sounds.hit);
                }
                case Event.Burst(double x, double y, double radius) -> {
                    blast(x, y, Pictures.blast(radius));
                    sparkle(x, y, 10, FIRE, 50);
                    dust(x, y, 8, DUST);
                    puff(x, y, 2);
                    if (heard(game, viewer, audible, x, y, null)) play(sounds.burst);
                }
                case Event.Destroyed(Unit unit) -> {
                    blast(unit.x(), unit.y(), 2);
                    sparkle(unit.x(), unit.y(), 16, FIRE, 60);
                    sparkle(unit.x(), unit.y(), 8, METAL, 40);
                    for (int i = 0; i < 3; i++) puff(unit.x(), unit.y(), 3);
                    wrecks.add(new Wreck(unit.x(), unit.y(), unit.type(), time));
                    if (heard(game, viewer, audible, unit.x(), unit.y(), unit.side())) play(sounds.boom);
                }
                case Event.Collapsed(Building building) -> {
                    BuildingType type = building.type();
                    for (int i = 0; i < type.width() * type.height(); i++) {
                        double x = (building.col() + random.nextDouble() * type.width()) * Field.TILE;
                        double y = (building.row() + random.nextDouble() * type.height()) * Field.TILE;
                        blast(x, y, 2);
                        puff(x, y, 4);
                        sparkle(x, y, 6, FIRE, 60);
                        dust(x, y, 6, METAL);
                    }
                    rubble.add(new Rubble(building.col() * Field.TILE, building.row() * Field.TILE, type, time));
                    if (heard(game, viewer, audible, building.x(), building.y(), building.side())) play(sounds.collapse);
                }
                case Event.Placed(Building building) -> {
                    BuildingType type = building.type();
                    for (int i = 0; i < 2 * (type.width() + type.height()); i++) {
                        double x = (building.col() + random.nextDouble() * type.width()) * Field.TILE;
                        double y = (building.row() + type.height()) * Field.TILE;
                        dust(x, y, 1, DUST);
                    }
                    if (building.side() == viewer && audible) play(sounds.placed);
                }
                case Event.Sold(Building building, int refund) -> {
                    dust(building.x(), building.y(), 20, DUST);
                    say(building.x(), building.y(), "+" + refund, Hud.AMBER);
                    if (building.side() == viewer && audible) play(sounds.credits);
                }
                case Event.Unloaded(Unit harvester, int credits) -> {
                    if (harvester.side() == viewer) {
                        say(harvester.x(), harvester.y() - 10, "+" + credits, Hud.AMBER);
                        if (audible) play(sounds.credits);
                    }
                }
                case Event.Ready(Side side, BuildingType type) -> {
                    if (side == viewer && audible) play(sounds.ready);
                }
                case Event.Deployed(Unit unit) -> {
                    if (unit.side() == viewer && audible) play(sounds.ready);
                }
                case Event.UnderAttack(Side side, double x, double y) -> {
                    if (side == viewer && audible) play(sounds.alarm);
                }
                case Event.BurrowerRose(double x, double y) -> {
                    dust(x, y, 24, DUST);
                    if (heard(game, viewer, audible, x, y, null)) play(sounds.burrower);
                }
                case Event.BurrowerAte(Unit unit) -> {
                    dust(unit.x(), unit.y(), 30, DUST);
                    sparkle(unit.x(), unit.y(), 8, METAL, 30);
                    if (audible && (unit.side() == viewer || visible(game, viewer, unit.x(), unit.y()))) play(sounds.boom);
                }
                case Event.Over(Side winner) -> {
                    if (audible && viewer != null) mixer.playMusic(winner == viewer ? music.won : music.lost, false);
                }
                case Event.StorageFull storageFull -> {
                }
            }
        }
    }
    // --8<-- [end:react]

    private static boolean visible(Game game, Side viewer, double x, double y) {
        if (viewer == null) return true;
        int col = (int) Math.floor(x / Field.TILE);
        int row = (int) Math.floor(y / Field.TILE);
        return game.house(viewer).fog().visible(col, row);
    }

    private static boolean heard(Game game, Side viewer, boolean audible, double x, double y, Side side) {
        return audible && (side != null && side == viewer || visible(game, viewer, x, y));
    }

    private void play(Sound sound) {
        Double last = played.get(sound);
        if (last != null && time - last < SOUND_GAP) return;
        played.put(sound, time);
        mixer.play(sound);
    }

    private void blast(double x, double y, int size) {
        blasts.add(new Blast(x, y, size, time));
    }

    /** Smoke rising from a point, of size 0 to 4. */
    private void puff(double x, double y, int size) {
        puffs.addLast(new Puff(x + (random.nextDouble() - 0.5) * 3, y + (random.nextDouble() - 0.5) * 3,
                Math.clamp(size + random.nextInt(2), 0, Pictures.PUFFS.length - 1), time,
                PUFF_SECONDS * (0.6 + random.nextDouble() * 0.4)));
        if (puffs.size() > MOST_PUFFS) puffs.removeFirst();
    }

    private void dust(double x, double y, int count, int colour) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * 2 * Math.PI;
            double v = 6 + random.nextDouble() * 18;
            particles.spawn(x, y, Math.cos(a) * v, Math.sin(a) * v, 0.5 + random.nextDouble() * 0.5,
                    1.5 + random.nextDouble() * 2.5, colour);
        }
    }

    private void sparkle(double x, double y, int count, int colour, double speed) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * 2 * Math.PI;
            double v = speed * (0.4 + random.nextDouble() * 0.8);
            particles.spawn(x, y, Math.cos(a) * v, Math.sin(a) * v, 0.2 + random.nextDouble() * 0.3,
                    0.7 + random.nextDouble() * 1.1, colour);
        }
    }

    private void say(double x, double y, String text, Color colour) {
        texts.add(new Text(x, y, text, colour, time));
    }

    // --8<-- [start:ground]
    /** On the ground, under the vehicles and the buildings: tracks, rubble, wrecks. */
    void renderGround(Graphics2D g, Camera camera, double alpha) {
        double left = camera.x(alpha) - 8;
        double top = camera.y(alpha) - 8;
        double right = left + camera.viewWidth() + 16;
        double bottom = top + camera.viewHeight() + 16;
        for (Track track : tracks) {
            if (track.x() < left || track.x() > right || track.y() < top || track.y() > bottom) continue;
            int shade = (int) ((time - track.born()) / TRACK_SECONDS * trackShades.length);
            g.setColor(trackShades[Math.min(trackShades.length - 1, shade)]);
            rectangle.setRect(track.x() + track.dx() - 0.7, track.y() + track.dy() - 0.7, 1.4, 1.4);
            g.fill(rectangle);
            rectangle.setRect(track.x() - track.dx() - 0.7, track.y() - track.dy() - 0.7, 1.4, 1.4);
            g.fill(rectangle);
        }
        // --8<-- [end:ground]
        Composite before = g.getComposite();
        for (Rubble r : rubble) {
            float a = (float) Math.min(1, (RUBBLE_SECONDS - (time - r.born())) / 5);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, a)));
            pictures.rubble(r.type()).draw(g, r.x(), r.y());
        }
        for (Wreck wreck : wrecks) {
            float a = (float) Math.min(1, (WRECK_SECONDS - (time - wreck.born())) / 5);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, a)));
            Sprite sprite = pictures.wreck(wreck.type());
            sprite.draw(g, wreck.x() - sprite.width() / 2, wreck.y() - sprite.height() / 2);
        }
        g.setComposite(before);
    }

    /** Where the burrower moves under the sand: its sign, and the ripples it leaves behind. */
    void renderBurrower(Graphics2D g, Burrower burrower, double alpha) {
        double x = burrower.x(alpha);
        double y = burrower.y(alpha);
        if (burrower.up()) {
            pictures.burrower.draw(g, x - pictures.burrower.width() / 2, y - pictures.burrower.height() / 2);
        } else {
            pictures.burrowerSign.draw(g, x - pictures.burrowerSign.width() / 2, y - pictures.burrowerSign.height() / 2);
        }
    }

    /** Above the vehicles: explosions, smoke and sparks, and the credits a harvester brings. */
    void render(Graphics2D g, double alpha) {
        for (Blast blast : blasts) {
            int frame = (int) ((time - blast.born()) / BLAST_SECONDS * Pictures.FRAMES);
            Sprite sprite = pictures.blasts[blast.size()][Math.min(Pictures.FRAMES - 1, frame)];
            sprite.draw(g, blast.x() - sprite.width() / 2, blast.y() - sprite.height() / 2);
        }
        particles.render(g, alpha);
        Composite before = g.getComposite();
        for (Puff puff : puffs) {
            double t = (time - puff.born()) / puff.life();
            if (t >= 1) continue;
            int size = Math.min(Pictures.PUFFS.length - 1, puff.size() + (int) (t * 2));
            Sprite sprite = pictures.puffs[size];
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.9 * (1 - t))));
            double age = time - puff.born();
            sprite.draw(g, puff.x() + age * WIND_X - sprite.width() / 2, puff.y() + age * WIND_Y - sprite.height() / 2);
        }
        g.setComposite(before);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        for (Text text : texts) {
            double t = (time - text.born()) / TEXT_SECONDS;
            int a = (int) Math.round(255 * Math.min(1, (1 - t) * 3));
            float x = (float) (text.x() - metrics.stringWidth(text.text()) / 2.0);
            float y = (float) (text.y() - 12 * t);
            g.setColor(new Color(0, 0, 0, a / 2));
            g.drawString(text.text(), x + 0.6f, y + 0.6f);
            Color c = text.colour();
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), a));
            g.drawString(text.text(), x, y);
        }
    }

    int particles() {
        return particles.count();
    }

    int tracks() {
        return tracks.size();
    }

    int wrecks() {
        return wrecks.size();
    }
}
