package eu.amadeos.games.sands.domain;

/** For the tests outside the rules: what only the rules may do, such as ending a side at once. */
public final class Cheat {
    private Cheat() {
    }

    /** Every building of {@code side} falls at the next step, so the other side wins. */
    public static void ruin(Game game, Side side) {
        for (Building building : game.buildings()) if (building.side() == side) building.health = 0;
    }

    /** A building of {@code side} at the first free place on the rock near its yard, built at once. */
    public static Building build(Game game, Side side, String type) {
        BuildingType building = game.rules().building(type);
        Building yard = game.buildings().stream().filter(b -> b.side() == side && b.type().yard()).findFirst()
                .orElseThrow();
        for (int r = 1; r < 12; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int col = yard.col() + dx;
                    int row = yard.row() + dy;
                    if (game.placement(side, building, col, row) == Game.Placement.OK) {
                        Building made = game.add(building, side, col, row);
                        game.account();
                        return made;
                    }
                }
            }
        }
        throw new IllegalStateException("no place for a " + type);
    }
}
