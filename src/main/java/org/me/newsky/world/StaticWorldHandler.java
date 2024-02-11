package org.me.newsky.world;

import org.me.newsky.NewSky;

import java.util.concurrent.CompletableFuture;

public class StaticWorldHandler extends WorldHandler {

    public StaticWorldHandler(NewSky plugin) {
        super(plugin);
    }

    @Override
    public CompletableFuture<Void> loadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (isWorldLoaded(worldName)) {
            future.complete(null);
            return future;
        }

        loadWorldToBukkit(worldName).thenRun(() -> {
            future.complete(null);
        }).exceptionally(e -> {
            future.completeExceptionally(e);
            return null;
        });

        return future;
    }

    @Override
    public CompletableFuture<Void> unloadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (!isWorldLoaded(worldName)) {
            future.complete(null);
            return future;
        }

        unloadWorldFromBukkit(worldName).thenRun(() -> {
            future.complete(null);
        }).exceptionally(e -> {
            future.completeExceptionally(e);
            return null;
        });

        return future;
    }
}
