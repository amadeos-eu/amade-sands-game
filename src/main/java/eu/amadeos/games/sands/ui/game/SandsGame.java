package eu.amadeos.games.sands.ui.game;

import com.google.inject.Inject;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.fog.FogOfWar;
import eu.amadeos.gameengine.input.Controls;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.loop.GameLoop;
import eu.amadeos.gameengine.minimap.Minimap;
import eu.amadeos.gameengine.scene.Scene;
import eu.amadeos.gameengine.scene.SceneStack;
import eu.amadeos.gameengine.select.BoxSelect;
import eu.amadeos.gameengine.select.Selection;
import eu.amadeos.gameengine.swing.GameCanvas;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.sands.domain.Building;
import eu.amadeos.games.sands.domain.BuildingType;
import eu.amadeos.games.sands.domain.Event;
import eu.amadeos.games.sands.domain.Field;
import eu.amadeos.games.sands.domain.Game;
import eu.amadeos.games.sands.domain.Level;
import eu.amadeos.games.sands.domain.Rules;
import eu.amadeos.games.sands.domain.Side;
import eu.amadeos.games.sands.domain.Thing;
import eu.amadeos.games.sands.domain.Unit;
import eu.amadeos.games.sands.domain.UnitType;
import eu.amadeos.games.sands.repository.Progress;
import eu.amadeos.games.sands.repository.SaveSlot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The running game: the loop, the mouse and the keys, the effects and the scenes (the title with the two
 * computers at war, the choice of a mission, a mission, the pause and its end). Everything here runs on the
 * game loop thread, except the {@link Arena} methods, which only post commands to it.
 */
public final class SandsGame implements Arena {
    enum Action { LEFT, RIGHT, UP, DOWN, PAUSE, BACK, OK, SAVE, LOAD, INFLUENCE, STOP }

    static final int UPDATES_PER_SECOND = 120;
    static final int FRAMES_PER_SECOND = 60;
    static final double STEP = 1.0 / UPDATES_PER_SECOND;
    /** How fast the keys and the edge of the screen move the map, in units per second. */
    static final double PAN_SPEED = 420;
    static final double EDGE = 3;
    /** How long a battle goes on behind its end before the result shows. */
    static final double END_SECONDS = 2;
    static final double TITLE_SPEED = 14;
    /** The title's battle runs this many times as fast, and starts again after this long. */
    static final int TITLE_STEPS = 2;
    static final double TITLE_SECONDS = 900;
    /** The player's side; the computer is the other. */
    static final Side PLAYER = Side.BLUE;
    static final double PAUSE_TOP = 170;
    static final double PAUSE_ROW = 24;

    private final Keyboard keyboard;
    private final Mouse mouse;
    private final Messages msg;
    private final Progress progress;
    private final SaveSlot slot;
    private final Mixer mixer;
    private final Rules rules;
    private final SandsPainter painter;
    private final Hud hud;
    private final Effects effects;
    private final SceneStack scenes = new SceneStack();
    private final Controls<Action> controls;
    private final List<Level> levels = new ArrayList<>();
    /** The ground of each map is made once, on other threads: it takes a moment. */
    private final Map<Integer, Terrain> terrains = new HashMap<>();
    private int chosen = 1;
    private long seed = System.nanoTime();
    private GameLoop loop;

    @Inject
    public SandsGame(Keyboard keyboard, Mouse mouse, Messages msg, Progress progress, SaveSlot slot, Mixer mixer) {
        this.keyboard = keyboard;
        this.mouse = mouse;
        this.msg = msg;
        this.progress = progress;
        this.slot = slot;
        this.mixer = mixer;
        this.rules = Rules.load();
        for (int i = 1; i <= rules.missions(); i++) levels.add(Level.load(i, rules));
        this.painter = new SandsPainter(rules);
        this.hud = new Hud(msg, painter.pictures());
        this.effects = new Effects(mixer, painter.pictures());
        // --8<-- [start:controls]
        controls = new Controls<>(Action.class, keyboard)
                .bind(Action.LEFT, KeyEvent.VK_LEFT)
                .bind(Action.RIGHT, KeyEvent.VK_RIGHT)
                .bind(Action.UP, KeyEvent.VK_UP)
                .bind(Action.DOWN, KeyEvent.VK_DOWN)
                .bind(Action.PAUSE, KeyEvent.VK_P)
                .bind(Action.BACK, KeyEvent.VK_ESCAPE)
                .bind(Action.OK, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)
                .bind(Action.SAVE, KeyEvent.VK_F5)
                .bind(Action.LOAD, KeyEvent.VK_F9)
                .bind(Action.INFLUENCE, KeyEvent.VK_F3)
                .bind(Action.STOP, KeyEvent.VK_S);
        // --8<-- [end:controls]
        scenes.push(new TitleScene());
    }

    /** Starts the loop; the canvas draws the scenes' worlds and then their overlays. */
    public void start(GameCanvas canvas) {
        loop = new GameLoop(UPDATES_PER_SECOND, FRAMES_PER_SECOND, this::update, canvas::render);
        loop.start();
    }

    public void stop() {
        if (loop != null) loop.stop();
    }

    void update(double seconds) {
        keyboard.poll();
        mouse.poll();
        scenes.update(seconds);
    }

    public void render(Graphics2D g, double alpha) {
        scenes.render(g, alpha);
    }

    public void renderOverlay(Graphics2D g, double alpha) {
        scenes.renderOverlay(g, alpha);
    }

    // ---- Arena: called on the Swing thread, carried out on the loop thread -----------------------

    @Override
    public void showTitle() {
        run(() -> scenes.replace(new TitleScene()));
    }

    @Override
    public void chooseLevel() {
        run(() -> scenes.replace(new ChoiceScene()));
    }

    @Override
    public void pause() {
        run(() -> {
            if (scenes.top() instanceof PlayScene play) scenes.push(new PauseScene(play));
        });
    }

    @Override
    public void setSound(boolean on) {
        mixer.setMuted(!on);
    }

    private void run(Runnable command) {
        if (loop != null && loop.running()) loop.post(command);
        else command.run();
    }

    // ---- for tests and screenshots ---------------------------------------------------------------

    Scene top() {
        return scenes.top();
    }

    int sceneCount() {
        return scenes.size();
    }

    Effects effects() {
        return effects;
    }

    Rules rules() {
        return rules;
    }

    Level level(int number) {
        return levels.get(number - 1);
    }

    /** Every map's ground drawn at once: for screenshots, which draw a single frame. */
    void settle() {
        terrains.values().forEach(Terrain::settle);
    }

    /** Plays mission {@code number} at once, without the title and the choice. */
    PlayScene play(int number) {
        return play(new Game(levels.get(number - 1), rules, seed++, Set.of(PLAYER.enemy())));
    }

    /** Plays a battle prepared by a test. */
    PlayScene play(Game battle) {
        PlayScene play = new PlayScene(battle);
        scenes.replace(play);
        return play;
    }

    // ---- the scenes ------------------------------------------------------------------------------

    private Terrain terrain(Game game) {
        Level level = game.level();
        Terrain terrain = terrains.computeIfAbsent(level.number(), number -> new Terrain(level));
        terrain.update(game);
        return terrain;
    }

    private static Camera camera(Level level) {
        return new Camera(Field.VIEW_WIDTH, Field.VIEW_HEIGHT, level.width() * Field.TILE, level.height() * Field.TILE);
    }

    private static Camera wideCamera(Level level) {
        return new Camera(Field.WIDTH, Field.HEIGHT, level.width() * Field.TILE, level.height() * Field.TILE);
    }

    /** The first map with both sides played by the computer; the view drifts across it. */
    final class TitleScene implements Scene {
        private Game demo;
        private final Camera camera = wideCamera(levels.getFirst());
        private double time;

        TitleScene() {
            effects.clear();
            restart();
        }

        private void restart() {
            demo = new Game(levels.getFirst(), rules, 7, EnumSet.allOf(Side.class));
            terrain(demo);
            time = 0;
        }

        @Override
        public void update(double seconds) {
            if (controls.wasPressed(Action.OK) || mouse.wasPressed(MouseEvent.BUTTON1)) {
                scenes.replace(new ChoiceScene());
                return;
            }
            if (controls.wasPressed(Action.LOAD) && load()) return;
            time += seconds;
            for (int i = 0; i < TITLE_STEPS; i++) {
                demo.update(seconds);
                effects.react(demo, null, false);
                effects.update(seconds, demo);
            }
            terrain(demo);
            if (demo.phase() != Game.Phase.PLAYING || time > TITLE_SECONDS) restart();
            double wayX = camera.worldWidth() - camera.viewWidth();
            double along = wayX <= 0 ? 0 : (time * TITLE_SPEED) % (2 * wayX);
            double x = along < wayX ? along : 2 * wayX - along;
            double y = (camera.worldHeight() - camera.viewHeight()) * (0.5 - 0.4 * Math.cos(time * 0.02));
            camera.moveBy(x - camera.x(), y - camera.y());
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            painter.paint(g, demo, null, terrain(demo), camera, effects, SandsPainter.Marks.NONE, alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("window.title"), 120, 72, true, Hud.SPICE);
            hud.text(g, msg.get("title.subtitle"), 150, 14, false, Hud.CHALK);
            hud.text(g, msg.get("title.start"), 210, 18, false, Hud.CHALK);
            hud.text(g, msg.get("title.mouse"), 262, 12, false, Hud.CHALK);
            hud.text(g, msg.get("title.keys"), 282, 12, false, Hud.CHALK);
            if (slot.exists()) hud.text(g, msg.get("title.load"), 302, 12, false, Hud.CHALK);
            hud.text(g, msg.get("title.screen"), 330, 11, false, Hud.GREY);
        }

        Game demo() {
            return demo;
        }
    }

    /** Loads the saved battle and plays it; false, with a buzz, when there is none. */
    private boolean load() {
        Optional<Game> saved = slot.load(rules);
        if (saved.isEmpty()) {
            effects.no();
            return false;
        }
        scenes.replace(new PlayScene(saved.get()));
        return true;
    }

    // --8<-- [start:choice]
    /** Three cards, one for each mission; one opens when the one before it is won. */
    final class ChoiceScene implements Scene {
        private final Map<Integer, Game> fields = new HashMap<>();
        private final Map<Integer, Camera> cameras = new HashMap<>();
        private double time;

        ChoiceScene() {
            effects.clear();
            chosen = Math.min(chosen, progress.open());
        }

        @Override
        public void update(double seconds) {
            time += seconds;
            if (controls.wasPressed(Action.BACK)) {
                scenes.replace(new TitleScene());
                return;
            }
            if (controls.wasPressed(Action.LOAD) && load()) return;
            int before = chosen;
            int open = Math.min(progress.open(), levels.size());
            if (controls.wasPressed(Action.LEFT)) chosen = Math.max(1, chosen - 1);
            if (controls.wasPressed(Action.RIGHT)) chosen = Math.min(open, chosen + 1);
            int card = cardAt(mouse.x(), mouse.y());
            if (mouse.wasMoved() && card > 0 && card <= open) chosen = card;
            if (chosen != before) effects.tick();
            boolean clicked = mouse.wasPressed(MouseEvent.BUTTON1) && card > 0 && card <= open;
            if (controls.wasPressed(Action.OK) || clicked) {
                scenes.replace(new PlayScene(new Game(levels.get(chosen - 1), rules, seed++, Set.of(PLAYER.enemy()))));
            }
        }
        // --8<-- [end:choice]

        int cardAt(double x, double y) {
            if (Double.isNaN(x) || y < Hud.CARD_TOP || y > Hud.CARD_TOP + Hud.CARD_HEIGHT) return 0;
            for (int i = 0; i < levels.size(); i++) {
                double left = Hud.cardX(i, levels.size());
                if (x >= left && x < left + Hud.CARD_WIDTH) return i + 1;
            }
            return 0;
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            Level level = levels.get(chosen - 1);
            Game field = fields.computeIfAbsent(chosen, n -> new Game(level, rules, 1, Set.of()));
            Camera camera = cameras.computeIfAbsent(chosen, n -> wideCamera(level));
            double wayX = camera.worldWidth() - camera.viewWidth();
            double along = wayX <= 0 ? 0 : (time * TITLE_SPEED) % (2 * wayX);
            camera.jumpTo(camera.viewWidth() / 2 + (along < wayX ? along : 2 * wayX - along), camera.worldHeight() / 2);
            painter.paint(g, field, null, terrain(field), camera, effects, SandsPainter.Marks.NONE, alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("select.title"), 70, 30, true, Hud.SPICE);
            for (int i = 0; i < levels.size(); i++) {
                int number = i + 1;
                hud.card(g, Hud.cardX(i, levels.size()), Hud.CARD_TOP, number, number <= progress.open(),
                        number == chosen, progress);
            }
            hud.text(g, msg.get("select.keys"), 330, 12, false, Hud.CHALK);
        }

        int chosen() {
            return chosen;
        }
    }

    // --8<-- [start:play]
    /**
     * A mission. The left mouse button chooses, a box drawn with it chooses many; the right button orders
     * what the chosen ones should do there. The panel builds. The map moves with the arrow keys, the middle
     * mouse button held down, the mouse at the edge of the screen, or a click on the minimap.
     */
    final class PlayScene implements Scene {
        private final Game game;
        private final Camera camera;
        private final Terrain terrain;
        private final Minimap minimap;
        private final Selection<Unit> selection = new Selection<>();
        private final BoxSelect box = new BoxSelect();
        private Building building;
        private boolean vehicles;
        /** The building the yard has ready, while the player looks for a place for it. */
        private BuildingType placing;
        private Cell place;
        private Game.Placement placement;
        private Cell order;
        private double orderAge = Double.MAX_VALUE;
        private int group;
        private boolean influence;
        private double dragX = Double.NaN;
        private double dragY = Double.NaN;
        private boolean minimapDrag;
        private String message;
        private Color messageColour = Hud.CHALK;
        private double messageShown = Double.MAX_VALUE;
        private int fogVersion = -1;
        private int spiceVersion = -1;
        private double ended;
        private boolean best;

        PlayScene(Game battle) {
            game = battle;
            Level level = battle.level();
            camera = SandsGame.camera(level);
            terrain = SandsGame.this.terrain(battle);
            minimap = new Minimap(Hud.MINIMAP.x, Hud.MINIMAP.y, Hud.MINIMAP.width, Hud.MINIMAP.height, level.width(),
                    level.height(), Field.TILE);
            for (Building b : battle.buildings()) {
                if (b.side() == PLAYER && b.type().yard()) camera.jumpTo(b.x(), b.y());
            }
            effects.clear();
            effects.music();
            say(msg.format("play.mission", level.number(), hud.name(level.number())), Hud.SPICE);
        }

        @Override
        public void update(double seconds) {
            if (game.phase() != Game.Phase.PLAYING) {
                ended += seconds;
                if (ended >= END_SECONDS) {
                    scenes.push(new ResultScene(this));
                    return;
                }
            }
            if (controls.wasPressed(Action.PAUSE)
                    || controls.wasPressed(Action.BACK) && placing == null && selection.isEmpty() && building == null) {
                scenes.push(new PauseScene(this));
                return;
            }
            if (controls.wasPressed(Action.LOAD) && load()) return;
            input(seconds);
            step(seconds);
            orderAge += seconds;
            messageShown += seconds;
        }

        /** One step of the rules, what it did, and the ground, the minimap and the choice after it. */
        void step(double seconds) {
            game.update(seconds);
            effects.react(game, PLAYER, true);
            effects.update(seconds, game);
            for (Event event : game.events()) tell(event);
            selection.keepOnly(Unit::alive);
            if (building != null && game.thing(building.id()) == null) building = null;
            if (placing != null && game.house(PLAYER).yard().ready() != placing) placing = null;
            terrain.update(game);
            FogOfWar fog = game.house(PLAYER).fog();
            if (fog.version() != fogVersion || game.spiceVersion() != spiceVersion) {
                fogVersion = fog.version();
                spiceVersion = game.spiceVersion();
                for (int row = 0; row < game.level().height(); row++) {
                    for (int col = 0; col < game.level().width(); col++) {
                        minimap.paint(col, row, Hud.minimapPixel(game, fog, col, row));
                    }
                }
            }
        }
        // --8<-- [end:play]

        // --8<-- [start:tell]
        /** What the player hears of: the message at the bottom of the map. */
        private void tell(Event event) {
            switch (event) {
                case Event.Ready(Side side, BuildingType type) when side == PLAYER -> {
                    say(msg.get("message.complete"), Hud.GREEN);
                    vehicles = false;
                }
                case Event.Deployed(Unit unit) when unit.side() == PLAYER ->
                        say(msg.format("message.deployed", msg.get("thing." + unit.type().id())), Hud.GREEN);
                case Event.UnderAttack(Side side, double x, double y) when side == PLAYER ->
                        say(msg.get("message.attack"), Hud.RED);
                case Event.StorageFull(Side side) when side == PLAYER -> say(msg.get("message.silos"), Hud.SPICE);
                case Event.Placed(Building placed) when placed.side() == PLAYER && game.house(PLAYER).lowPower() ->
                        say(msg.get("message.power"), Hud.RED);
                case Event.Collapsed(Building fallen) when fallen.side() == PLAYER ->
                        say(msg.format("message.lost", msg.get("thing." + fallen.type().id())), Hud.RED);
                case Event.Collapsed(Building fallen) when fallen.side() != PLAYER ->
                        say(msg.format("message.destroyed", msg.get("thing." + fallen.type().id())), Hud.GREEN);
                case Event.WormRose(double x, double y) when visible(x, y) -> say(msg.get("message.worm"), Hud.SPICE);
                case Event.WormAte(Unit unit) when unit.side() == PLAYER ->
                        say(msg.format("message.eaten", msg.get("thing." + unit.type().id())), Hud.RED);
                case Event.Over(Side winner) -> {
                    if (winner == PLAYER) {
                        best = progress.won(game.level().number(), (int) game.time(), levels.size());
                        chosen = Math.min(levels.size(), game.level().number() + 1);
                    }
                }
                default -> {
                }
            }
        }
        // --8<-- [end:tell]

        private boolean visible(double x, double y) {
            return game.house(PLAYER).fog().visible((int) Math.floor(x / Field.TILE), (int) Math.floor(y / Field.TILE));
        }

        private void say(String text, Color colour) {
            message = text;
            messageColour = colour;
            messageShown = 0;
        }

        // --8<-- [start:input]
        private void input(double seconds) {
            double mx = mouse.x();
            double my = mouse.y();
            boolean onMap = !Double.isNaN(mx) && mx < Field.VIEW_WIDTH;
            double wx = camera.worldX(mx);
            double wy = camera.worldY(my);
            boolean adding = keyboard.isDown(KeyEvent.VK_SHIFT);
            boolean ctrl = keyboard.isDown(KeyEvent.VK_CONTROL);

            if (controls.wasPressed(Action.BACK)) {
                if (placing != null) placing = null;
                else {
                    selection.clear();
                    building = null;
                }
            }
            if (controls.wasPressed(Action.SAVE)) {
                slot.save(game);
                say(msg.get("message.saved"), Hud.CHALK);
                effects.tick();
            }
            if (controls.wasPressed(Action.INFLUENCE)) influence = !influence;
            if (controls.wasPressed(Action.STOP) && !selection.isEmpty()) game.stop(selection.all());
            groups(ctrl);

            placing(onMap, wx, wy);
            if (mouse.wasPressed(MouseEvent.BUTTON1)) {
                if (onMap) leftPress(wx, wy);
                else panelClick(mx, my, false);
            }
            if (mouse.wasPressed(MouseEvent.BUTTON3)) {
                if (onMap) command(wx, wy, ctrl);
                else panelClick(mx, my, true);
            }
            if (minimapDrag && mouse.isDown(MouseEvent.BUTTON1) && !Double.isNaN(mx)) {
                camera.jumpTo(minimap.worldX(Math.clamp(mx, minimap.x(), minimap.x() + minimap.width())),
                        minimap.worldY(Math.clamp(my, minimap.y(), minimap.y() + minimap.height())));
            }
            if (!mouse.isDown(MouseEvent.BUTTON1)) minimapDrag = false;
            box.move(wx, wy);
            if (mouse.wasReleased(MouseEvent.BUTTON1)) release(wx, wy, adding);
            pan(seconds, mx, my);
        }
        // --8<-- [end:input]

        /** Ctrl and a number keeps the chosen as a group; the number alone chooses them again. */
        private void groups(boolean ctrl) {
            for (int number = 1; number < Selection.GROUPS; number++) {
                if (!keyboard.wasPressed(KeyEvent.VK_0 + number)) continue;
                if (ctrl) {
                    selection.store(number);
                    group = number;
                    effects.tick();
                } else if (selection.recall(number)) {
                    group = number;
                    building = null;
                    effects.tick();
                }
            }
        }

        /** Where the building ready would stand, its middle under the mouse. */
        private void placing(boolean onMap, double wx, double wy) {
            if (placing == null || !onMap) {
                place = null;
                return;
            }
            int col = (int) Math.floor(wx / Field.TILE - (placing.width() - 1) / 2.0);
            int row = (int) Math.floor(wy / Field.TILE - (placing.height() - 1) / 2.0);
            place = new Cell(col, row);
            placement = game.placement(PLAYER, placing, col, row);
        }

        private void leftPress(double wx, double wy) {
            if (placing != null) {
                if (place == null) return;
                Game.Placement result = game.place(PLAYER, place.col(), place.row());
                if (result == Game.Placement.OK) placing = null;
                else refuse(result);
                return;
            }
            box.press(wx, wy);
        }

        // --8<-- [start:release]
        /** The left button came up: a click chooses what is under it, a box all of the player's vehicles in it. */
        private void release(double wx, double wy, boolean adding) {
            BoxSelect.Result result = box.release(wx, wy);
            switch (result) {
                case null -> {
                }
                case BoxSelect.Click(double x, double y) -> {
                    Unit unit = game.unitAt(x, y);
                    Building there = game.building((int) Math.floor(x / Field.TILE), (int) Math.floor(y / Field.TILE));
                    if (unit != null && unit.side() == PLAYER) {
                        selection.click(unit, adding);
                        building = null;
                        group = 0;
                        effects.yes();
                    } else if (there != null && (there.side() == PLAYER || game.visible(PLAYER, there))) {
                        selection.clear();
                        building = there;
                        effects.tick();
                    } else if (!adding) {
                        selection.clear();
                        building = null;
                    }
                }
                case BoxSelect.Box picked -> {
                    List<Unit> inside = new ArrayList<>();
                    for (Unit unit : game.units()) {
                        if (unit.side() == PLAYER && picked.contains(unit.x(), unit.y())) inside.add(unit);
                    }
                    if (!inside.isEmpty() || !adding) selection.pick(inside, adding);
                    if (!inside.isEmpty()) {
                        building = null;
                        group = 0;
                        effects.yes();
                    }
                }
            }
        }
        // --8<-- [end:release]

        // --8<-- [start:command]
        /**
         * The right button on the map: the chosen attack an enemy under it, harvesters go to spice or home
         * to a refinery, the rest drive there; with Ctrl they fight their way there. Without anything chosen,
         * or while a building waits for its place, it takes back the choice.
         */
        private void command(double wx, double wy, boolean hunt) {
            if (placing != null) {
                placing = null;
                return;
            }
            List<Unit> chosen = selection.all();
            if (chosen.isEmpty()) {
                building = null;
                return;
            }
            int col = (int) Math.floor(wx / Field.TILE);
            int row = (int) Math.floor(wy / Field.TILE);
            if (!game.contains(col, row)) return;
            Cell cell = new Cell(col, row);
            Thing enemy = enemyAt(wx, wy, col, row);
            boolean harvesters = chosen.stream().anyMatch(unit -> unit.type().harvests());
            Building there = game.building(col, row);
            if (enemy != null) {
                game.attack(chosen, enemy);
            } else if (harvesters && there != null && there.side() == PLAYER && there.type().dock() >= 0) {
                game.bringHome(chosen);
            } else if (harvesters && game.spice(col, row) > 0) {
                game.harvest(chosen, cell);
            } else if (hunt) {
                game.hunt(chosen, cell);
            } else {
                game.move(chosen, cell);
            }
            order = cell;
            orderAge = 0;
            effects.yes();
        }
        // --8<-- [end:command]

        /** An enemy the player sees under a point. */
        private Thing enemyAt(double wx, double wy, int col, int row) {
            Unit unit = game.unitAt(wx, wy);
            if (unit != null && unit.side() != PLAYER && game.visible(PLAYER, unit)) return unit;
            Building there = game.building(col, row);
            if (there != null && there.side() != PLAYER && game.visible(PLAYER, there)) return there;
            return null;
        }

        // --8<-- [start:panel]
        /** A click on the panel: the minimap, the tabs, the build list, the buttons. */
        private void panelClick(double mx, double my, boolean right) {
            if (minimap.contains(mx, my)) {
                if (!game.radar(PLAYER)) return;
                if (right && !selection.isEmpty()) {
                    int col = (int) Math.floor(minimap.worldX(mx) / Field.TILE);
                    int row = (int) Math.floor(minimap.worldY(my) / Field.TILE);
                    game.move(selection.all(), new Cell(col, row));
                    effects.yes();
                } else if (!right) {
                    camera.jumpTo(minimap.worldX(mx), minimap.worldY(my));
                    minimapDrag = true;
                }
                return;
            }
            if (right) {
                int cell = Hud.cellAt(mx, my);
                if (cell >= 0) cancel(cell);
                return;
            }
            if (Hud.BUILDINGS_TAB.contains(mx, my)) switchTab(false);
            else if (Hud.VEHICLES_TAB.contains(mx, my)) switchTab(true);
            else if (Hud.STOP.contains(mx, my) && !selection.isEmpty()) {
                game.stop(selection.all());
                effects.tick();
            } else if (Hud.SELL.contains(mx, my) && building != null && building.side() == PLAYER
                    && building.type().buildable()) {
                game.sell(building);
                building = null;
            } else if (Hud.MENU.contains(mx, my)) {
                scenes.push(new PauseScene(this));
            } else {
                int cell = Hud.cellAt(mx, my);
                if (cell >= 0) order(cell);
            }
        }

        private void switchTab(boolean toVehicles) {
            if (vehicles != toVehicles) effects.tick();
            vehicles = toVehicles;
        }

        /**
         * A click on the build list. A building that is ready goes looking for its place; else the yard
         * builds one thing at a time. The factory takes up to five orders.
         */
        private void order(int cell) {
            if (vehicles) {
                List<UnitType> types = Hud.vehicles(game, PLAYER);
                if (cell >= types.size()) return;
                if (game.house(PLAYER).factory().queue().size() >= 5 || !game.produce(PLAYER, types.get(cell))) {
                    effects.no();
                } else {
                    effects.tick();
                }
                return;
            }
            List<BuildingType> types = Hud.buildings(game, PLAYER);
            if (cell >= types.size()) return;
            BuildingType type = types.get(cell);
            var yard = game.house(PLAYER).yard();
            if (yard.ready() == type) {
                placing = type;
                selection.clear();
                building = null;
                effects.tick();
            } else if (yard.isEmpty() && yard.ready() == null && game.build(PLAYER, type)) {
                effects.tick();
            } else {
                effects.no();
            }
        }
        // --8<-- [end:panel]

        private void cancel(int cell) {
            boolean done;
            if (vehicles) {
                List<UnitType> types = Hud.vehicles(game, PLAYER);
                done = cell < types.size() && game.cancel(PLAYER, types.get(cell));
            } else {
                List<BuildingType> types = Hud.buildings(game, PLAYER);
                done = cell < types.size() && game.cancel(PLAYER, types.get(cell));
                if (done && placing == types.get(cell)) placing = null;
            }
            if (done) effects.tick();
        }

        private void refuse(Game.Placement why) {
            effects.no();
            String key = switch (why) {
                case NOT_ROCK -> "place.rock";
                case OCCUPIED -> "place.occupied";
                case TOO_FAR -> "place.far";
                default -> "place.outside";
            };
            say(msg.get(key), Hud.RED);
        }

        /** The map moves by the keys, by the edge of the screen, and by dragging it with the middle button. */
        private void pan(double seconds, double mx, double my) {
            boolean dragging = mouse.isDown(MouseEvent.BUTTON2);
            double dx = controls.axis(Action.LEFT, Action.RIGHT) * PAN_SPEED * seconds;
            double dy = controls.axis(Action.UP, Action.DOWN) * PAN_SPEED * seconds;
            if (mouse.isInside() && !dragging && !box.dragging() && !minimapDrag && !Double.isNaN(mx)) {
                if (mx < EDGE) dx -= PAN_SPEED * seconds;
                if (mx > Field.WIDTH - EDGE) dx += PAN_SPEED * seconds;
                if (my < EDGE) dy -= PAN_SPEED * seconds;
                if (my > Field.HEIGHT - EDGE) dy += PAN_SPEED * seconds;
            }
            if (dragging && !Double.isNaN(dragX)) {
                dx += dragX - mx;
                dy += dragY - my;
            }
            dragX = dragging ? mx : Double.NaN;
            dragY = dragging ? my : Double.NaN;
            camera.moveBy(dx, dy);
        }

        SandsPainter.Marks marks() {
            return new SandsPainter.Marks(selection.all(), building, placing, place, placement,
                    box.dragging() ? box.box() : null, order, orderAge,
                    influence ? game.commander(PLAYER.enemy()) : null);
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            painter.paint(g, game, PLAYER, terrain, camera, effects, marks(), alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.panel(g, game, PLAYER, minimap, camera,
                    new Hud.View(vehicles, selection.all(), building, group, mouse.x(), mouse.y()), alpha);
            hud.corner(g, Hud.clock((int) game.time()), 12);
            if (influence) hud.corner(g, msg.get("play.influence"), 24);
            hud.message(g, message, messageShown, messageColour);
        }

        Game game() {
            return game;
        }

        Camera camera() {
            return camera;
        }

        Selection<Unit> selection() {
            return selection;
        }

        Terrain terrain() {
            return terrain;
        }

        Minimap minimap() {
            return minimap;
        }

        BuildingType placing() {
            return placing;
        }

        boolean vehicles() {
            return vehicles;
        }

        Building building() {
            return building;
        }

        String message() {
            return messageShown < Hud.MESSAGE_SECONDS ? message : null;
        }
    }

    // --8<-- [start:pause]
    /** The battle stands still: go on, save it, load the saved one, or leave it. */
    final class PauseScene implements Scene {
        enum Choice { CONTINUE, SAVE, LOAD, LEAVE }

        private final PlayScene play;
        private int highlighted;
        private String note;

        PauseScene(PlayScene play) {
            this.play = play;
        }

        @Override
        public void update(double seconds) {
            if (controls.wasPressed(Action.PAUSE)) {
                scenes.pop();
                return;
            }
            if (controls.wasPressed(Action.BACK)) {
                choose(Choice.LEAVE);
                return;
            }
            if (controls.wasPressed(Action.SAVE)) choose(Choice.SAVE);
            if (controls.wasPressed(Action.LOAD)) {
                choose(Choice.LOAD);
                return;
            }
            int before = highlighted;
            if (controls.wasPressed(Action.UP)) highlighted = Math.max(0, highlighted - 1);
            if (controls.wasPressed(Action.DOWN)) highlighted = Math.min(Choice.values().length - 1, highlighted + 1);
            int row = rowAt(mouse.x(), mouse.y());
            if (mouse.wasMoved() && row >= 0) highlighted = row;
            if (highlighted != before) effects.tick();
            if (controls.wasPressed(Action.OK) || mouse.wasPressed(MouseEvent.BUTTON1) && row >= 0) {
                choose(Choice.values()[highlighted]);
            }
        }
        // --8<-- [end:pause]

        private void choose(Choice choice) {
            switch (choice) {
                case CONTINUE -> scenes.pop();
                case SAVE -> {
                    slot.save(play.game);
                    note = msg.get("message.saved");
                    effects.tick();
                }
                case LOAD -> {
                    if (!load()) note = msg.get("pause.nothing");
                }
                case LEAVE -> scenes.replace(new ChoiceScene());
            }
        }

        int rowAt(double x, double y) {
            if (Double.isNaN(x) || Math.abs(x - Field.WIDTH / 2) > 90) return -1;
            int row = (int) Math.floor((y - PAUSE_TOP + PAUSE_ROW * 0.7) / PAUSE_ROW);
            return row >= 0 && row < Choice.values().length ? row : -1;
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("pause.title"), 120, 36, true, Hud.CHALK);
            String[] keys = {"pause.continue", "pause.save", "pause.load", "pause.leave"};
            for (int i = 0; i < keys.length; i++) {
                hud.text(g, msg.get(keys[i]), PAUSE_TOP + i * PAUSE_ROW, 15, i == highlighted,
                        i == highlighted ? Hud.SPICE : Hud.CHALK);
            }
            if (note != null) hud.text(g, note, PAUSE_TOP + 4 * PAUSE_ROW + 10, 12, false, Hud.GREEN);
        }

        int highlighted() {
            return highlighted;
        }
    }

    /** The end of a mission: the battlefield stays behind the result; Enter goes on or tries again. */
    final class ResultScene implements Scene {
        private final PlayScene play;
        private double shown;

        ResultScene(PlayScene play) {
            this.play = play;
        }

        @Override
        public void update(double seconds) {
            effects.update(seconds, null);
            shown += seconds;
            if (shown < 0.6) return;     // a short wait, so a click meant for the battle does not skip the result
            if (controls.wasPressed(Action.BACK)) {
                scenes.replace(new ChoiceScene());
                return;
            }
            if (controls.wasPressed(Action.OK) || mouse.wasPressed(MouseEvent.BUTTON1)) {
                int number = play.game.level().number();
                boolean won = play.game.winner() == PLAYER;
                if (won && number == levels.size()) scenes.replace(new ChoiceScene());
                else scenes.replace(new PlayScene(new Game(levels.get(won ? number : number - 1), rules, seed++,
                        Set.of(PLAYER.enemy()))));
            }
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.result(g, play.game, PLAYER, play.best, play.game.level().number() == levels.size());
        }
    }
}
