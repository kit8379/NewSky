package org.me.newsky.model;

import java.util.UUID;

public class PlayerName {
    private final UUID uuid;
    private final String name;

    public PlayerName(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }
}
