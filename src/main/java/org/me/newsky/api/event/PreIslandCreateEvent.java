package org.me.newsky.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;


/**
 * Event called when an island is going to be created
 * Triggered in the server that the island is created on
 */
public class PreIslandCreateEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final UUID islandUuid;
    private final UUID playerUuid;


    /**
     * Create a new PostIslandCreateEvent
     *
     * @param islandUuid The UUID of the island that was created
     * @param playerUuid The UUID of the player that the island was created for
     */
    public PreIslandCreateEvent(UUID islandUuid, UUID playerUuid) {
        this.islandUuid = islandUuid;
        this.playerUuid = playerUuid;
    }

    /**
     * Get the HandlerList for this event
     *
     * @return The HandlerList for this event
     */
    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() {
        return handlers;
    }


    /**
     * Get the UUID of the island that was created
     *
     * @return The UUID of the island
     */
    @SuppressWarnings("unused")
    public UUID getIslandUuid() {
        return islandUuid;
    }


    /**
     * Get the UUID of the player that the island was created for
     *
     * @return The UUID of the player
     */
    @SuppressWarnings("unused")
    public UUID getPlayerUuid() {
        return playerUuid;
    }


    /**
     * Get the HandlerList for this event
     *
     * @return The HandlerList for this event
     */
    @Override
    @NotNull
    @SuppressWarnings("unused")
    public HandlerList getHandlers() {
        return handlers;
    }
}