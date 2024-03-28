package org.me.newsky.util;

import java.util.UUID;

public class IslandUtils {

    /**
     * Converts an island name to a UUID.
     *
     * @param islandName the name of the island
     * @return the UUID of the island
     */
    public static UUID nameToUUID(String islandName) {
        return UUID.fromString(islandName.substring(7));
    }

    /**
     * Converts an island UUID to a name.
     *
     * @param islandUuid the UUID of the island
     * @return the name of the island
     */
    public static String UUIDToName(UUID islandUuid) {
        return "island-" + islandUuid.toString();
    }
}
