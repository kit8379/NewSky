package org.me.newsky.model;

import java.util.UUID;

public class IslandPlayer {
    private final UUID islandUuid;
    private final UUID playerUuid;
    private final String role;

    public IslandPlayer(UUID islandUuid, UUID playerUuid, String role) {
        this.islandUuid = islandUuid;
        this.playerUuid = playerUuid;
        this.role = role;
    }

    public UUID getIslandUuid() {
        return islandUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getRole() {
        return role;
    }
}
