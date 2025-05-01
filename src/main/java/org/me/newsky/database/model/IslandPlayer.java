package org.me.newsky.database.model;

import java.util.UUID;

public class IslandPlayer {
    private final UUID playerUuid;
    private final UUID islandUuid;
    private final String role;

    public IslandPlayer(UUID playerUuid, UUID islandUuid, String role) {
        this.playerUuid = playerUuid;
        this.islandUuid = islandUuid;
        this.role = role;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public UUID getIslandUuid() {
        return islandUuid;
    }

    public String getRole() {
        return role;
    }
}
