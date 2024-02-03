package org.me.newsky.world;

import org.me.newsky.NewSky;

import java.util.concurrent.CompletableFuture;

public class DynamicWorldHandler extends WorldHandler {

    public DynamicWorldHandler(NewSky plugin) {
        super(plugin);
    }

    @Override
    public CompletableFuture<Void> createWorld(String worldName) {
        // TODO: Implement createWorld method for dynamic mode
        return null;
    }

    @Override
    public CompletableFuture<Void> loadWorld(String worldName) {
        // TODO: Implement loadWorld method for dynamic mode
        return null;
    }

    @Override
    public CompletableFuture<Void> unloadWorld(String worldName) {
        // TODO: Implement unloadWorld method for dynamic mode
        return null;
    }

    @Override
    public CompletableFuture<Void> deleteWorld(String worldName) {
        // TODO: Implement deleteWorld method for dynamic mode
        return null;
    }
}
