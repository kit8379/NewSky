package org.me.newsky.world;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;

public class DynamicWorldHandler extends WorldHandler {
    private final Path storagePath;

    public DynamicWorldHandler(NewSky plugin, ConfigHandler config, Path storagePath) {
        super(plugin, config);
        this.storagePath = storagePath;
    }

    @Override
    public CompletableFuture<Void> loadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            Path worldPath = storagePath.resolve(worldName);
            Path targetPath = plugin.getServer().getWorldContainer().toPath().resolve(worldName);

            if (Files.exists(worldPath) && !Files.exists(targetPath)) {
                try {
                    moveDirectory(worldPath, targetPath);
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

        unloadWorldFromBukkit(worldName).thenRunAsync(() -> {
            Path worldPath = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
            Path targetPath = storagePath.resolve(worldName);

            if (Files.exists(worldPath) && !Files.exists(targetPath)) {
                try {
                    moveDirectory(worldPath, targetPath);
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
        unloadWorldFromBukkit(worldName).thenRunAsync(() -> {
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

    private void moveDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.move(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc == null) {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                } else {
                    throw exc;
                }
            }
        });
    }
}
