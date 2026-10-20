package eu.amadeos.games.sands.domain;

/**
 * What a mission gives the two houses, and how the computer plays it, from {@code missions.txt}. Times in
 * seconds.
 *
 * @param army  the vehicles of its first attack; each later attack brings {@code grows} more
 */
public record Mission(int number, String map, int credits, int enemyCredits, boolean burrower, double attack, double every,
                      int army, int grows, int turrets) {
}
