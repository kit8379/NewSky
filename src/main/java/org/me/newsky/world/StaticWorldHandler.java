package org.me.newsky.world;

import org.me.newsky.NewSky;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class StaticWorldHandler extends WorldHandler {

    public StaticWorldHandler(NewSky plugin) {
        super(plugin);
    }

    @Override
    public CompletableFuture<Void> createWorld(String worldName) {
        plugin.debug("Creating world: " + worldName);
        CompletableFuture<Void> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                plugin.debug("Copying template for world: " + worldName);
                copyTemplateWorld(worldName);
                plugin.debug("Template copied for world: " + worldName);
            } catch (IOException e) {
                plugin.debug("Error copying template" + " for world: " + worldName + " - " + e.getMessage());
                future.completeExceptionally(e);
            }
        }).thenCompose(aVoid -> loadWorldToBukkit(worldName)).thenRun(() -> future.complete(null)).exceptionally(e -> {
            plugin.debug("Exception in creating world: " + worldName + " - " + e.getMessage());
            future.completeExceptionally(e);
            return null;
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> loadWorld(String worldName) {
        plugin.debug("Loading world: " + worldName);
        CompletableFuture<Void> future = new CompletableFuture<>();
        loadWorldToBukkit(worldName).thenRun(() -> future.complete(null)).exceptionally(e -> {
            plugin.debug("Exception loading world: " + worldName + " - " + e.getMessage());
            future.completeExceptionally(e);
            return null;
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> unloadWorld(String worldName) {
        plugin.debug("Unloading world: " + worldName);
        CompletableFuture<Void> future = new CompletableFuture<>();
        unloadWorldFromBukkit(worldName).thenRun(() -> future.complete(null)).exceptionally(e -> {
            plugin.debug("Exception unloading world: " + worldName + " - " + e.getMessage());
            future.completeExceptionally(e);
            return null;
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> deleteWorld(String worldName) {
        plugin.debug("Deleting world: " + worldName);
        CompletableFuture<Void> future = new CompletableFuture<>();
        unloadWorldFromBukkit(worldName).thenRunAsync(() -> {
            try {
                plugin.debug("Deleting directory for world: " + worldName);
                Path worldDirectory = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
                deleteDirectory(worldDirectory);
                plugin.debug("Directory deleted for world: " + worldName);
                future.complete(null);
            } catch (IOException e) {
                plugin.debug("Error deleting world: " + worldName + " - " + e.getMessage());
                future.completeExceptionally(e);
            }
        }).exceptionally(e -> {
            plugin.debug("Exception in deleting world: " + worldName + " - " + e.getMessage());
            future.completeExceptionally(e);
            return null;
        });
        return future;
    }
}
