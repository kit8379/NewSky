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

        if (isWorldLoaded(worldName)) {
            future.complete(null);
            return future;
        }

        if (Files.exists(worldPath)) {
            try {
                moveDirectory(worldPath, plugin.getServer().getWorldContainer().toPath().resolve(worldName));
                return loadWorldToBukkit(worldName);
            } catch (IOException e) {
                future.completeExceptionally(e);
                return future;
            }
        } else {
            future.completeExceptionally(new IOException("World directory does not exist in the storage path: " + worldPath));
            return future;
        }
    }


    @Override
    public CompletableFuture<Void> unloadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (!isWorldLoaded(worldName)) {
            future.complete(null);
            return future;
        }

        unloadWorldFromBukkit(worldName).thenRun(() -> {
            Path worldPath = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
            Path targetPath = storagePath.resolve(worldName);

            try {
                moveDirectory(worldPath, targetPath);
                future.complete(null);
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
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
