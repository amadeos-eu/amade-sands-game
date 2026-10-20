package eu.amadeos.games.sands.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.save.SaveFile;
import eu.amadeos.games.sands.bootstrap.SandsModule;
import eu.amadeos.games.sands.domain.Building;
import eu.amadeos.games.sands.domain.BuildingType;
import eu.amadeos.games.sands.domain.Field;
import eu.amadeos.games.sands.domain.Game;
import eu.amadeos.games.sands.domain.Side;
import eu.amadeos.games.sands.domain.Unit;
import eu.amadeos.games.sands.repository.Progress;
import eu.amadeos.games.sands.repository.SaveSlot;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The screenshots of chapter 31, drawn without a screen: the title, the choice of a mission, a base with a
 * group of tanks sent off, a building looking for its place, a battle, the burrower, and the computer's view
 * of the player's strength. {@code ./gradlew screenshots -Pshots=<folder>}.
 */
public final class Screenshots {
    private final long[] now = {0};
    private final Keyboard keyboard = new Keyboard(() -> now[0]);
    private final Mouse mouse = new Mouse();
    private final SandsGame game;
    private double mouseX;
    private double mouseY;

    private Screenshots() throws Exception {
        Messages msg = Messages.load(SandsModule.class, SandsModule.MESSAGES, Locale.ENGLISH);
        Path dir = Files.createTempDirectory("sands");
        Progress progress = new Progress(new SaveFile(dir.resolve("progress.save")));
        progress.won(1, 754, 3);      // one mission won, so the choice shows a time and a lock
        game = new SandsGame(keyboard, mouse, msg, progress, new SaveSlot(new SaveFile(dir.resolve("battle.save"))),
                new Mixer());
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "screenshots");
        dir.mkdirs();
        Screenshots shots = new Screenshots();
        shots.steps(40 * 120);
        shots.save(new File(dir, "title.png"), false);
        shots.tap(KeyEvent.VK_ENTER);
        shots.steps(60);
        shots.save(new File(dir, "choice.png"), false);

        // Mission 1, both sides played by the computer for a while: a base, harvesters at work, an army.
        SandsGame.PlayScene base = shots.battle(1, 31, 380);
        Building yard = base.game().buildings().stream()
                .filter(b -> b.side() == Side.BLUE && b.type().yard()).findFirst().orElseThrow();
        List<Unit> tanks = base.game().units().stream()
                .filter(u -> u.side() == Side.BLUE && u.type().id().equals("tank")).limit(5).toList();
        base.camera().jumpTo(yard.x() + 150, yard.y() + 90);
        base.selection().pick(tanks, false);
        shots.steps(2);
        shots.point(base, yard.x() + 260, yard.y() + 120);
        shots.rightClick();
        shots.steps(50);
        shots.save(new File(dir, "base.png"), true);

        SandsGame.PlayScene placing = shots.placing();
        shots.save(new File(dir, "placing.png"), true);

        SandsGame.PlayScene fight = shots.battle(3, 5, 600);
        shots.lookAtFight(fight, 5);
        shots.save(new File(dir, "fight.png"), false);
        shots.tap(KeyEvent.VK_F3);
        shots.save(new File(dir, "influence.png"), false);
        shots.tap(KeyEvent.VK_F3);
        System.exit(0);
    }

    /** Mission {@code number} with both sides played by the computer for {@code seconds}, seen by blue. */
    private SandsGame.PlayScene battle(int number, long seed, double seconds) {
        Game battle = new Game(game.level(number), game.rules(), seed, EnumSet.allOf(Side.class));
        SandsGame.PlayScene play = game.play(battle);
        steps((int) (seconds * SandsGame.UPDATES_PER_SECOND));
        return play;
    }

    /** Mission 1 with a turbine ready: the player looks for its place next to the yard. */
    private SandsGame.PlayScene placing() {
        Game battle = new Game(game.level(1), game.rules(), 3, Set.of(Side.RED));
        SandsGame.PlayScene play = game.play(battle);
        battle.build(Side.BLUE, game.rules().building("turbine"));
        steps(20 * SandsGame.UPDATES_PER_SECOND);
        Building yard = battle.buildings().stream().filter(b -> b.side() == Side.BLUE && b.type().yard())
                .findFirst().orElseThrow();
        play.camera().jumpTo(yard.x() + 60, yard.y() + 40);
        steps(2);
        mouseX = Hud.cell(0).getCenterX();
        mouseY = Hud.cell(0).getCenterY();
        mouse.moved(mouseX, mouseY);
        steps(2);
        click();
        BuildingType turbine = game.rules().building("turbine");
        for (int d = 1; d < 12; d++) {
            for (int dy = -d; dy <= d; dy++) {
                for (int dx = -d; dx <= d; dx++) {
                    int col = yard.col() + yard.type().width() + dx;
                    int row = yard.row() + dy;
                    if (battle.placement(Side.BLUE, turbine, col, row) == Game.Placement.OK) {
                        point(play, (col + 1) * Field.TILE, (row + 1) * Field.TILE);
                        steps(5);
                        return play;
                    }
                }
            }
        }
        throw new IllegalStateException("no place for a turbine");
    }

    /**
     * The camera over a battle that blue sees: a blue vehicle with at least {@code count} red things in sight
     * near it and shells in the air.
     */
    private void lookAtFight(SandsGame.PlayScene play, int count) {
        for (int i = 0; i < 8000; i++) {
            Game battle = play.game();
            if (!battle.shots().isEmpty()) {
                for (Unit unit : battle.units()) {
                    if (unit.side() != Side.BLUE) continue;
                    List<Unit> near = battle.units().stream().filter(u -> u.side() == Side.RED
                            && battle.visible(Side.BLUE, u) && Math.hypot(u.x() - unit.x(), u.y() - unit.y()) < 150)
                            .toList();
                    if (near.size() >= count) {
                        play.camera().jumpTo((unit.x() + near.getFirst().x()) / 2, (unit.y() + near.getFirst().y()) / 2);
                        steps(1);
                        return;
                    }
                }
            }
            steps(3);
        }
        throw new IllegalStateException("no battle: " + play.game().phase() + " at " + play.game().time());
    }

    private void point(SandsGame.PlayScene play, double worldX, double worldY) {
        mouseX = play.camera().screenX(worldX, 1);
        mouseY = play.camera().screenY(worldY, 1);
        mouse.moved(mouseX, mouseY);
        steps(2);
    }

    private void click() {
        mouse.pressed(MouseEvent.BUTTON1);
        steps(1);
        mouse.released(MouseEvent.BUTTON1);
        steps(1);
    }

    private void rightClick() {
        mouse.pressed(MouseEvent.BUTTON3);
        steps(1);
        mouse.released(MouseEvent.BUTTON3);
        steps(1);
    }

    private void tap(int key) {
        keyboard.pressed(key, now[0]);
        keyboard.released(key, now[0] + 1_000_000);
        steps(5);
    }

    private void steps(int n) {
        for (int i = 0; i < n; i++) {
            now[0] += (long) (SandsGame.STEP * 1e9);
            game.update(SandsGame.STEP);
        }
    }

    /** World and overlay at twice the logical size, with the mouse pointer if {@code pointer}. */
    private void save(File file, boolean pointer) throws Exception {
        game.settle();
        write(file, g -> {
            game.render(g, 1);
            game.renderOverlay(g, 1);
            if (pointer) pointer(g, mouseX, mouseY);
        });
    }

    private static void pointer(Graphics2D g, double x, double y) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x, y + 13);
        p.lineTo(x + 3.5, y + 10);
        p.lineTo(x + 6, y + 15);
        p.lineTo(x + 8, y + 14);
        p.lineTo(x + 5.5, y + 9.5);
        p.lineTo(x + 9.5, y + 9.5);
        p.closePath();
        g.setColor(Color.WHITE);
        g.fill(p);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1f));
        g.draw(p);
    }

    private static void write(File file, Consumer<Graphics2D> draw) throws Exception {
        int scale = 2;
        BufferedImage image = new BufferedImage((int) Field.WIDTH * scale, (int) Field.HEIGHT * scale,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(scale, scale);
        draw.accept(g);
        g.dispose();
        ImageIO.write(image, "png", file);
    }

    static List<String> names() {
        return List.of("title", "choice", "base", "placing", "fight", "influence");
    }

}
