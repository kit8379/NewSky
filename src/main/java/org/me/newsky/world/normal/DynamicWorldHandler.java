package org.me.newsky.world.normal;

import org.bukkit.World;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class DynamicWorldHandler extends NormalWorldHandler {
    private final Path storagePath;

    public DynamicWorldHandler(NewSky plugin, ConfigHandler config) {
        super(plugin, config);
        this.storagePath = Path.of(config.getStoragePath());
    }

    @Override
    public CompletableFuture<Void> loadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            Path worldPath = storagePath.resolve(worldName);
            Path targetPath = plugin.getServer().getWorldContainer().toPath().resolve(worldName);

            if (Files.exists(worldPath) && !Files.exists(targetPath)) {
                try {
                    copyDirectory(worldPath, targetPath);
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            }
        }).thenCompose(aVoid -> {
            return loadWorldToBukkit(worldName);
        }).thenRun(() -> {
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

        unloadWorldFromBukkit(worldName, true).thenRunAsync(() -> {
            Path worldPath = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
            Path targetPath = storagePath.resolve(worldName);

            if (Files.exists(worldPath) && !Files.exists(targetPath)) {
                try {
                    copyDirectory(worldPath, targetPath);
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            }
            future.complete(null);
        }).exceptionally(e -> {
            future.completeExceptionally(e);
            return null;
        });

        return future;
    }

    @Override
    public CompletableFuture<Void> deleteWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // First, ensure the world is unloaded properly
        unloadWorldFromBukkit(worldName, false).thenRunAsync(() -> {
            Path worldPath = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
            Path targetPath = storagePath.resolve(worldName);

            if (Files.exists(worldPath)) {
                try {
                    deleteDirectory(worldPath);
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            }

            if (Files.exists(targetPath)) {
                try {
                    deleteDirectory(targetPath);
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            }
            future.complete(null);
        }).exceptionally(e -> {
            future.completeExceptionally(e);
            return null;
        });

        return future;
    }

    @Override
    public void saveWorld(String worldName) {
        // Copy the world to the storage directory in async
        CompletableFuture.runAsync(() -> {
            plugin.info("Saving world " + worldName + " to storage directory.");
            Path worldPath = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
            Path targetPath = storagePath.resolve(worldName);

            if (Files.exists(worldPath)) {
                try {
                    copyDirectory(worldPath, targetPath);
                    plugin.info("World " + worldName + " saved to storage directory.");
                } catch (IOException e) {
                    plugin.info("Failed to save world " + worldName + " to storage directory.");
                    e.printStackTrace();
                }
            }
        });
    }
}
