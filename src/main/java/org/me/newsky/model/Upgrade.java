package org.me.newsky.model;

/**
 * One upgrade as a member sees it before buying: the island's current level and, unless the
 * upgrade is maxed, the next level's requirements against that island and the member's
 * balance. Money is pre-formatted by the economy provider; the next-level fields are unset
 * when maxed.
 */
public record Upgrade(int currentLevel, int currentLimit, boolean maxed, int nextLevel, int nextLimit, int nextRequireLevel, String nextPrice, boolean available, int islandLevel, String balance) {
}
