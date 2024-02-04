package org.me.newsky.world;

import org.me.newsky.NewSky;

import java.util.concurrent.CompletableFuture;

public class StaticWorldHandler extends WorldHandler {

    public StaticWorldHandler(NewSky plugin) {
        super(plugin);
    }

    @Override
    public CompletableFuture<Void> loadWorld(String worldName) {
        plugin.debug("Attempting to load world: " + worldName);
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (isWorldLoaded(worldName)) {
            plugin.debug("World " + worldName + " is already loaded.");
            future.complete(null);
        } else {
            loadWorldToBukkit(worldName).thenRun(() -> future.complete(null)).exceptionally(e -> {
                plugin.debug("Exception loading world: " + worldName + " - " + e.getMessage());
                future.completeExceptionally(e);
                return null;
            });
        }
        return future;
    }

    @Override
    public CompletableFuture<Void> unloadWorld(String worldName) {
        plugin.debug("Attempting to unload world: " + worldName);
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (!isWorldLoaded(worldName)) {
            plugin.debug("World " + worldName + " is not loaded.");
            future.complete(null);
        } else {
            unloadWorldFromBukkit(worldName).thenRun(() -> future.complete(null)).exceptionally(e -> {
                plugin.debug("Exception unloading world: " + worldName + " - " + e.getMessage());
                future.completeExceptionally(e);
                return null;
            });
        }
        return future;
    }
}
