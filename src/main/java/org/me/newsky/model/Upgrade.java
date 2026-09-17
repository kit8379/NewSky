package org.me.newsky.model;

/**
 * One upgrade as a member sees it before buying: the island's current level and, unless the
 * upgrade is maxed, the next level's requirements against that island and the member's
 * balance. Values are pre-formatted for display (a limit, a size or the allowed biome list)
 * and money by the economy provider; the next-level fields are unset when maxed.
 */
public record Upgrade(int currentLevel, String currentValue, boolean maxed, int nextLevel, String nextValue, int nextRequireLevel, String nextPrice, boolean available, int islandLevel, String balance) {
}
