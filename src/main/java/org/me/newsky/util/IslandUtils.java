package org.me.newsky.util;

import java.util.UUID;

public final class IslandUtils {

    private static final String ISLAND_WORLD_PREFIX = "island-";
    private static final int UUID_LENGTH = 36;

    private IslandUtils() {
    }

    /**
     * Converts an island UUID to its world name.
     *
     * @param islandUuid the UUID of the island
     * @return the name of the island world
     */
    public static String UUIDToName(UUID islandUuid) {
        return ISLAND_WORLD_PREFIX + islandUuid;
    }

    /**
     * Whether the world name carries the island prefix. A cheap gate only - a prefixed name is
     * not necessarily a well-formed island world. Use {@link #parseIslandUuid(String)} whenever
     * the UUID itself is needed.
     *
     * @param worldName the name of the world
     * @return true if the name starts with the island prefix
     */
    public static boolean isIslandWorld(String worldName) {
        return worldName != null && worldName.startsWith(ISLAND_WORLD_PREFIX);
    }

    /**
     * Resolves a world name to its island UUID, or null if the name is not a well-formed island
     * world. Never throws: this runs on per-block-event paths, and a foreign world that merely
     * starts with the island prefix must be treated as "not ours", not blow up the listener.
     * <p>
     * Allocation-free parse of the canonical form the plugin itself generates via
     * {@link #UUIDToName(UUID)} - deliberately stricter than {@link UUID#fromString(String)}.
     *
     * @param worldName the name of the world
     * @return the island UUID, or null if the world is not a well-formed island world
     */
    public static UUID parseIslandUuid(String worldName) {
        if (worldName == null || worldName.length() != ISLAND_WORLD_PREFIX.length() + UUID_LENGTH || !worldName.startsWith(ISLAND_WORLD_PREFIX)) {
            return null;
        }

        long msb = 0L;
        long lsb = 0L;

        for (int i = 0; i < UUID_LENGTH; i++) {
            char c = worldName.charAt(ISLAND_WORLD_PREFIX.length() + i);

            if (i == 8 || i == 13 || i == 18 || i == 23) {
                if (c != '-') {
                    return null;
                }
                continue;
            }

            int digit = Character.digit(c, 16);
            if (digit < 0) {
                return null;
            }

            if (i < 18) {
                msb = (msb << 4) | digit;
            } else {
                lsb = (lsb << 4) | digit;
            }
        }

        return new UUID(msb, lsb);
    }

    /**
     * Checks whether a home or warp name is legal. Names are limited to lowercase
     * alphanumerics, underscores and hyphens so that they stay safe as command arguments.
     *
     * @param name the already-normalized (lowercase) name
     * @return true if the name is legal, false otherwise
     */
    public static boolean isLegalPointName(String name) {
        if (name == null || name.isEmpty() || name.length() > 32) {
            return false;
        }

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != '-') {
                return false;
            }
        }

        return true;
    }
}
