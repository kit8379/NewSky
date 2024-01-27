package org.me.newsky.world;

import org.bukkit.Bukkit;
import org.bukkit.WorldCreator;
import org.me.newsky.NewSky;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class WorldHandler {

    private final NewSky plugin;

    public WorldHandler(NewSky plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> createWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            copyTemplateWorld(worldName);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            new WorldCreator(worldName).generator(new VoidGenerator()).createWorld();
            future.complete(null);
        });

        return future;
    }

    private void copyTemplateWorld(String worldName) throws IOException {
        Path sourceDirectory = plugin.getDataFolder().toPath().resolve("template/skyblock");
        Path targetDirectory = plugin.getServer().getWorldContainer().toPath().resolve(worldName);

        try (Stream<Path> stream = Files.walk(sourceDirectory)) {
            stream.forEach(sourcePath -> {
                Path targetPath = targetDirectory.resolve(sourceDirectory.relativize(sourcePath));
                try {
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Error copying template world: " + e.getMessage(), e);
                }
            });
        }
    }

    public CompletableFuture<Void> deleteWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.unloadWorld(worldName, false);
            try {
                Path worldDirectory = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
                deleteDirectory(worldDirectory);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }


    private void deleteDirectory(Path path) {
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (!file.delete()) {
                            throw new RuntimeException("Failed to delete file: " + file.getAbsolutePath());
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Error deleting world directory: " + e.getMessage(), e);
        }
    }
}
