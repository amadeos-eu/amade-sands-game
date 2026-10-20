package eu.amadeos.games.sands.domain;

/**
 * An enemy building as a side last saw it. Buildings do not move, so what was seen once stays on the map
 * under the fog, until someone looks again and finds it gone.
 */
public record Ghost(int id, BuildingType type, Side side, int col, int row) {
}
