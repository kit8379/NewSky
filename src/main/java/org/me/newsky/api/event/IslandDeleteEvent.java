package org.me.newsky.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;


/**
 * Event called when an island is deleted
 * Only called in the server that the island is deleted on
 */
public class IslandDeleteEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final UUID islandUuid;


    /**
     * Create a new IslandDeleteEvent
     *
     * @param islandUuid The UUID of the island that was deleted
     */
    public IslandDeleteEvent(UUID islandUuid) {
        this.islandUuid = islandUuid;
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
     * Get the UUID of the island that was deleted
     *
     * @return The UUID of the island
     */
    @SuppressWarnings("unused")
    public UUID getIslandUuid() {
        return islandUuid;
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