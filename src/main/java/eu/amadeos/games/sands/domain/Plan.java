package eu.amadeos.games.sands.domain;

import java.util.List;

/**
 * How the computer builds, from {@code commander.txt}: its buildings in order, the vehicles of its army
 * in turn, and how many harvesters it keeps for each refinery.
 */
public record Plan(List<BuildingType> build, List<UnitType> mix, int harvesters) {
    public Plan {
        build = List.copyOf(build);
        mix = List.copyOf(mix);
    }
}
