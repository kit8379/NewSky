package org.me.newsky.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;

import java.io.File;
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
        return CompletableFuture.runAsync(() -> {
            try {
                copyTemplateWorld(worldName);
            } catch (IOException e) {
                throw new RuntimeException("Error copying template world: " + e.getMessage(), e);
            }
        }).thenRunAsync(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                loadWorld(worldName);
            });
        });
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
        return unloadWorld(worldName).thenRunAsync(() -> {
            try {
                Path worldDirectory = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
                if (!deleteDirectory(worldDirectory)) {
                    throw new RuntimeException("Failed to delete some files in world directory");
                }
            } catch (IOException e) {
                throw new RuntimeException("Error deleting world directory: " + e.getMessage(), e);
            }
        });
    }

    private boolean deleteDirectory(Path path) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void loadWorld(String worldName) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            new WorldCreator(worldName).generator(new VoidGenerator()).createWorld();
        });
    }

    public CompletableFuture<Void> unloadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                removePlayersFromWorld(world);
                if (!Bukkit.unloadWorld(world, false)) {
                    throw new IllegalStateException("Failed to unload world: " + worldName);
                }
            } else {
                throw new IllegalStateException("World not found: " + worldName);
            }
            future.complete(null);
        });

        return future;
    }

    private void removePlayersFromWorld(World world) {
        World safeWorld = Bukkit.getServer().getWorlds().get(0);
        for (Player player : world.getPlayers()) {
            player.teleport(safeWorld.getSpawnLocation());
        }
    }
}
