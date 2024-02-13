package org.me.newsky.world;

import org.me.newsky.NewSky;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;

public class DynamicWorldHandler extends WorldHandler {
    private final Path storagePath;

    public DynamicWorldHandler(NewSky plugin, Path storagePath) {
        super(plugin);
        this.storagePath = storagePath;
    }

    @Override
    public CompletableFuture<Void> loadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Path worldPath = storagePath.resolve(worldName);
        if (Files.exists(worldPath) && !Files.exists(plugin.getServer().getWorldContainer().toPath().resolve(worldName))) {
            try {
                moveDirectory(worldPath, plugin.getServer().getWorldContainer().toPath().resolve(worldName));
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        }

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
            Path worldPath = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
            Path targetPath = storagePath.resolve(worldName);

            try {
                moveDirectory(worldPath, targetPath);
                future.complete(null);
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
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
            try {
                Path worldDirectory = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
                Path storageDirectory = storagePath.resolve(worldName);

                if (Files.exists(worldDirectory)) {
                    deleteDirectory(worldDirectory);
                }

                if (Files.exists(storageDirectory)) {
                    deleteDirectory(storageDirectory);
                }

                future.complete(null);
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
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
