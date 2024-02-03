package org.me.newsky.world;

import org.me.newsky.NewSky;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

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

        if (Files.exists(worldPath)) {
            try {
                moveDirectory(worldPath, plugin.getServer().getWorldContainer().toPath().resolve(worldName));
                plugin.debug("Moved world " + worldName + " from storage to server.");
            } catch (IOException e) {
                plugin.debug("Failed to move world " + worldName + " from storage to server: " + e.getMessage());
                future.completeExceptionally(e);
                return future;
            }
        }
        return loadWorldToBukkit(worldName);
    }

    @Override
    public CompletableFuture<Void> unloadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        unloadWorldFromBukkit(worldName).thenRun(() -> {
            Path worldPath = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
            Path targetPath = storagePath.resolve(worldName);

            try {
                moveDirectory(worldPath, targetPath);
                plugin.debug("Moved world " + worldName + " from server to storage.");
                future.complete(null);
            } catch (IOException e) {
                plugin.debug("Failed to move world " + worldName + " from server to storage: " + e.getMessage());
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            paths.forEach(sourcePath -> {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                try {
                    Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException("Error moving file from " + sourcePath + " to " + targetPath, e);
                }
            });
        }
    }
}
