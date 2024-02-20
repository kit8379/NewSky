package org.me.newsky.world.normal;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.world.normal.NormalWorldHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class StaticWorldHandler extends NormalWorldHandler {

    public StaticWorldHandler(NewSky plugin, ConfigHandler config) {
        super(plugin, config);
    }

    @Override
    public CompletableFuture<Void> loadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        loadWorldToBukkit(worldName).thenRunAsync(() -> {
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

        unloadWorldFromBukkit(worldName).thenRunAsync(() -> {
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

        unloadWorldFromBukkit(worldName).thenRunAsync(() -> {
            Path worldPath = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
            if (Files.exists(worldPath)) {
                try {
                    deleteDirectory(worldPath);
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
}
