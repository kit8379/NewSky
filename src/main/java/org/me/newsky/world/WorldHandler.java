package org.me.newsky.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.world.generator.VoidGenerator;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;

public abstract class WorldHandler {

    protected final NewSky plugin;

    public WorldHandler(NewSky plugin) {
        this.plugin = plugin;
    }

    public abstract CompletableFuture<Void> createWorld(String worldName);

    public abstract CompletableFuture<Void> loadWorld(String worldName);

    public abstract CompletableFuture<Void> unloadWorld(String worldName);

    public abstract CompletableFuture<Void> deleteWorld(String worldName);

    protected void copyTemplateWorld(String worldName) throws IOException {
        Path sourceDirectory = plugin.getDataFolder().toPath().resolve("template/skyblock");
        Path targetDirectory = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
        copyDirectory(sourceDirectory, targetDirectory);
    }

    protected CompletableFuture<Void> loadWorldToBukkit(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                WorldCreator worldCreator = new WorldCreator(worldName).generator(new VoidGenerator());
                Bukkit.createWorld(worldCreator);
                plugin.debug("World created in Bukkit: " + worldName);
                future.complete(null);
            } else {
                plugin.debug("World already loaded in Bukkit: " + worldName);
                future.completeExceptionally(new IllegalStateException("World already loaded: " + worldName));
            }
        });
        return future;
    }

    protected CompletableFuture<Void> unloadWorldFromBukkit(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                removePlayersFromWorld(world);
                if (Bukkit.unloadWorld(world, true)) {
                    plugin.debug("World unloaded in Bukkit: " + worldName);
                    future.complete(null);
                } else {
                    plugin.debug("Failed to unload world in Bukkit: " + worldName);
                    future.completeExceptionally(new IllegalStateException("Failed to unload world: " + worldName));
                }
            } else {
                plugin.debug("World not found for unloading in Bukkit: " + worldName);
                future.completeExceptionally(new IllegalStateException("World not found: " + worldName));
            }
        });
        return future;
    }

    protected void deleteDirectory(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    protected void removePlayersFromWorld(World world) {
        World safeWorld = Bukkit.getServer().getWorlds().get(0);
        for (Player player : world.getPlayers()) {
            player.teleport(safeWorld.getSpawnLocation());
        }
    }

    protected void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
