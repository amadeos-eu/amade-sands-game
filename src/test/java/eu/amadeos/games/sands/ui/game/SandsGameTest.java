package eu.amadeos.games.sands.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.save.SaveFile;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.sands.bootstrap.SandsModule;
import eu.amadeos.games.sands.domain.Building;
import eu.amadeos.games.sands.domain.BuildingType;
import eu.amadeos.games.sands.domain.Field;
import eu.amadeos.games.sands.domain.Game;
import eu.amadeos.games.sands.domain.Ground;
import eu.amadeos.games.sands.domain.Cheat;
import eu.amadeos.games.sands.domain.Side;
import eu.amadeos.games.sands.domain.Unit;
import eu.amadeos.games.sands.repository.Progress;
import eu.amadeos.games.sands.repository.SaveSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The game without a window and without the loop thread: the test moves the mouse, clicks, presses keys and
 * calls the steps itself, so everything happens in order on the test thread.
 */
class SandsGameTest {
    private static final double STEP = SandsGame.STEP;

    @TempDir
    Path dir;

    private final long[] now = {0};
    private final Keyboard keyboard = new Keyboard(() -> now[0]);
    private final Mouse mouse = new Mouse();
    private Progress progress;
    private SaveSlot slot;
    private SandsGame game;

    @BeforeEach
    void create() {
        Messages msg = Messages.load(SandsModule.class, SandsModule.MESSAGES, Locale.ENGLISH);
        progress = new Progress(new SaveFile(dir.resolve("progress.save")));
        slot = new SaveSlot(new SaveFile(dir.resolve("battle.save")));
        game = new SandsGame(keyboard, mouse, msg, progress, slot, new Mixer());
    }

    private void tap(int key) {
        keyboard.pressed(key, now[0]);
        keyboard.released(key, now[0] + 1_000_000);
        steps(5);
    }

    private void steps(int n) {
        for (int i = 0; i < n; i++) {
            now[0] += (long) (STEP * 1e9);
            game.update(STEP);
        }
    }

    private void seconds(double seconds) {
        steps((int) Math.round(seconds * SandsGame.UPDATES_PER_SECOND));
    }

    private void moveTo(double x, double y) {
        mouse.moved(x, y);
        steps(1);
    }

    /** The mouse over a point of the world. */
    private void over(SandsGame.PlayScene play, double worldX, double worldY) {
        moveTo(play.camera().screenX(worldX, 1), play.camera().screenY(worldY, 1));
    }

    private void click(int button) {
        mouse.pressed(button);
        steps(1);
        mouse.released(button);
        steps(1);
    }

    /** Mission 1 against the computer, as the choice would start it. */
    private SandsGame.PlayScene mission() {
        return game.play(new Game(game.level(1), game.rules(), 1, Set.of(Side.RED)));
    }

    private static List<Unit> vehicles(SandsGame.PlayScene play, String kind) {
        return play.game().units().stream().filter(u -> u.side() == Side.BLUE && u.type().id().equals(kind)).toList();
    }

    private static Building yard(SandsGame.PlayScene play) {
        return play.game().buildings().stream().filter(b -> b.side() == Side.BLUE && b.type().yard()).findFirst()
                .orElseThrow();
    }

    /** Open sand near a point, in sight of the camera. */
    private static Cell sand(SandsGame.PlayScene play, double x, double y) {
        Game g = play.game();
        int col = (int) (x / Field.TILE);
        int row = (int) (y / Field.TILE);
        for (int r = 0; r < 20; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int c = col + dx;
                    int w = row + dy;
                    if (g.contains(c, w) && g.level().ground(c, w) == Ground.SAND && g.open(c, w) && g.crystal(c, w) == 0
                            && g.building(c, w) == null) {
                        return new Cell(c, w);
                    }
                }
            }
        }
        throw new AssertionError("no sand");
    }

    @Test
    void fromTheTitleToTheFirstMission() {
        assertInstanceOf(SandsGame.TitleScene.class, game.top());
        tap(KeyEvent.VK_ENTER);
        SandsGame.ChoiceScene choice = assertInstanceOf(SandsGame.ChoiceScene.class, game.top());
        tap(KeyEvent.VK_RIGHT);
        assertEquals(1, choice.chosen(), "mission 2 is not open yet");
        tap(KeyEvent.VK_ENTER);
        SandsGame.PlayScene play = assertInstanceOf(SandsGame.PlayScene.class, game.top());
        assertEquals(1, play.game().level().number());
        assertEquals("Mission 1 · First Crystals", play.message());
    }

    @Test
    void aClickChoosesOneVehicleAndABoxAllInIt() {
        SandsGame.PlayScene play = mission();
        steps(2);
        Unit trike = vehicles(play, "trike").getFirst();
        over(play, trike.x(), trike.y());
        click(MouseEvent.BUTTON1);
        assertEquals(List.of(trike), play.selection().all());

        List<Unit> mine = play.game().units().stream().filter(u -> u.side() == Side.BLUE
                && u.x() > play.camera().x() && u.x() < play.camera().x() + Field.VIEW_WIDTH - 1
                && u.y() > play.camera().y() && u.y() < play.camera().y() + Field.VIEW_HEIGHT - 1).toList();
        assertTrue(mine.size() >= 2, "a few vehicles in view: " + mine.size());
        moveTo(1, 1);
        mouse.pressed(MouseEvent.BUTTON1);
        steps(1);
        moveTo(Field.VIEW_WIDTH - 2, Field.HEIGHT - 2);
        mouse.released(MouseEvent.BUTTON1);
        steps(1);
        assertEquals(Set.copyOf(mine), Set.copyOf(play.selection().all()), "the box takes all the player's in view");
    }

    @Test
    void theRightButtonSendsTheChosenAndCtrlWithANumberKeepsThemAsAGroup() {
        SandsGame.PlayScene play = mission();
        steps(2);
        Unit trike = vehicles(play, "trike").getFirst();
        play.selection().pick(List.of(trike), false);
        Cell target = sand(play, trike.x() + 80, trike.y() + 40);
        over(play, (target.col() + 0.5) * Field.TILE, (target.row() + 0.5) * Field.TILE);
        click(MouseEvent.BUTTON3);
        assertEquals(target, trike.goal());

        keyboard.pressed(KeyEvent.VK_CONTROL, now[0]);
        tap(KeyEvent.VK_1);
        keyboard.released(KeyEvent.VK_CONTROL, now[0]);
        tap(KeyEvent.VK_ESCAPE);
        assertTrue(play.selection().isEmpty(), "Esc takes back the choice");
        tap(KeyEvent.VK_1);
        assertEquals(List.of(trike), play.selection().all(), "1 chooses group 1 again");
    }

    @Test
    void thePanelBuildsATurbineAndTheMouseGivesItItsPlace() {
        SandsGame.PlayScene play = mission();
        BuildingType turbine = game.rules().building("turbine");
        assertSame(turbine, Hud.buildings(play.game(), Side.BLUE).getFirst());
        moveTo(Hud.cell(0).getCenterX(), Hud.cell(0).getCenterY());
        click(MouseEvent.BUTTON1);
        assertSame(turbine, play.game().house(Side.BLUE).yard().current());
        seconds(turbine.seconds() + 1);
        assertSame(turbine, play.game().house(Side.BLUE).yard().ready());
        click(MouseEvent.BUTTON1);
        assertSame(turbine, play.placing(), "the turbine goes looking for its place");

        Cell sand = sand(play, yard(play).x(), yard(play).y());
        over(play, (sand.col() + 0.5) * Field.TILE, (sand.row() + 0.5) * Field.TILE);
        click(MouseEvent.BUTTON1);
        assertNotNull(play.placing(), "not on the sand");
        assertEquals("Buildings stand on rock only", play.message());

        Building yard = yard(play);
        int traps = play.game().count(Side.BLUE, turbine);
        for (int row = yard.row() - 3; row < yard.row() + 8 && play.placing() != null; row++) {
            for (int col = yard.col() - 3; col < yard.col() + 8 && play.placing() != null; col++) {
                if (play.game().placement(Side.BLUE, turbine, col, row) != Game.Placement.OK) continue;
                over(play, (col + 1) * Field.TILE, (row + 1) * Field.TILE);
                click(MouseEvent.BUTTON1);
            }
        }
        assertNull(play.placing());
        assertEquals(traps + 1, play.game().count(Side.BLUE, turbine));
    }

    @Test
    void theMinimapNeedsAnOutpostAndAClickOnItMovesTheView() {
        SandsGame.PlayScene play = mission();
        steps(2);
        double before = play.camera().x();
        moveTo(play.minimap().x() + play.minimap().width() - 2, play.minimap().y() + play.minimap().height() - 2);
        click(MouseEvent.BUTTON1);
        assertEquals(before, play.camera().x(), "no radar yet");
        Cheat.build(play.game(), Side.BLUE, "outpost");
        if (play.game().house(Side.BLUE).lowPower()) Cheat.build(play.game(), Side.BLUE, "turbine");
        steps(2);
        moveTo(play.minimap().x() + play.minimap().width() - 2, play.minimap().y() + play.minimap().height() - 2);
        click(MouseEvent.BUTTON1);
        assertNotEquals(before, play.camera().x());
        assertTrue(play.camera().y() > 0);
    }

    @Test
    void f5SavesAndF9LoadsTheBattle() {
        SandsGame.PlayScene play = mission();
        seconds(3);
        tap(KeyEvent.VK_F5);
        double saved = play.game().time();
        assertTrue(slot.exists());
        seconds(2);
        tap(KeyEvent.VK_F9);
        SandsGame.PlayScene loaded = assertInstanceOf(SandsGame.PlayScene.class, game.top());
        assertNotEquals(play, loaded);
        assertEquals(saved, loaded.game().time(), 0.1);
    }

    @Test
    void pausingStopsTheBattleAndLeavingGoesBackToTheChoice() {
        SandsGame.PlayScene play = mission();
        steps(2);
        tap(KeyEvent.VK_P);
        assertInstanceOf(SandsGame.PauseScene.class, game.top());
        double time = play.game().time();
        seconds(1);
        assertEquals(time, play.game().time());
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_ENTER);
        assertTrue(slot.exists(), "the second line saves");
        tap(KeyEvent.VK_ESCAPE);
        assertInstanceOf(SandsGame.ChoiceScene.class, game.top());
    }

    @Test
    void aWonMissionShowsTheResultAndOpensTheNext() {
        SandsGame.PlayScene play = mission();
        steps(2);
        Cheat.ruin(play.game(), Side.RED);
        seconds(SandsGame.END_SECONDS + 0.5);
        assertInstanceOf(SandsGame.ResultScene.class, game.top());
        assertEquals(2, progress.open());
        seconds(1);
        tap(KeyEvent.VK_ENTER);
        SandsGame.PlayScene next = assertInstanceOf(SandsGame.PlayScene.class, game.top());
        assertEquals(2, next.game().level().number());
    }

    @Test
    void everySceneDraws() {
        BufferedImage image = new BufferedImage((int) Field.WIDTH, (int) Field.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        steps(10);
        game.render(g, 1);
        game.renderOverlay(g, 1);
        SandsGame.PlayScene play = mission();
        seconds(1);
        game.settle();
        assertTrue(play.terrain().settled(), "the ground is drawn in the background, and all of it is ready");
        tap(KeyEvent.VK_F3);
        game.render(g, 0.5);
        game.renderOverlay(g, 0.5);
        tap(KeyEvent.VK_P);
        game.render(g, 1);
        game.renderOverlay(g, 1);
        g.dispose();
        assertNotEquals(0, image.getRGB((int) Field.VIEW_WIDTH / 2, (int) Field.HEIGHT / 2) & 0xFFFFFF);
    }
}
