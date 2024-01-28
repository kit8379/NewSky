package org.me.newsky.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;

public class WorldHandler {

    private final NewSky plugin;

    public WorldHandler(NewSky plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> createWorld(String worldName) {
        plugin.debug("Creating world: " + worldName);
        CompletableFuture<Void> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
                    try {
                        plugin.debug("Copying template for world: " + worldName);
                        copyTemplateWorld(worldName);
                        plugin.debug("Template copied for world: " + worldName);
                    } catch (IOException e) {
                        plugin.debug("Error copying template for world: " + worldName + " - " + e.getMessage());
                        future.completeExceptionally(e);
                    }
                }).thenCompose(aVoid -> loadWorldToBukkit(worldName))
                .thenRun(() -> future.complete(null))
                .exceptionally(e -> {
                    plugin.debug("Exception in creating world: " + worldName + " - " + e.getMessage());
                    future.completeExceptionally(e);
                    return null;
                });
        return future;
    }

    public CompletableFuture<Void> loadWorld(String worldName) {
        plugin.debug("Loading world: " + worldName);
        CompletableFuture<Void> future = new CompletableFuture<>();
        loadWorldToBukkit(worldName)
                .thenRun(() -> future.complete(null))
                .exceptionally(e -> {
                    plugin.debug("Exception loading world: " + worldName + " - " + e.getMessage());
                    future.completeExceptionally(e);
                    return null;
                });
        return future;
    }

    public CompletableFuture<Void> unloadWorld(String worldName) {
        plugin.debug("Unloading world: " + worldName);
        CompletableFuture<Void> future = new CompletableFuture<>();
        unloadWorldFromBukkit(worldName)
                .thenRun(() -> future.complete(null))
                .exceptionally(e -> {
                    plugin.debug("Exception unloading world: " + worldName + " - " + e.getMessage());
                    future.completeExceptionally(e);
                    return null;
                });
        return future;
    }

    public CompletableFuture<Void> deleteWorld(String worldName) {
        plugin.debug("Deleting world: " + worldName);
        CompletableFuture<Void> future = new CompletableFuture<>();
        unloadWorldFromBukkit(worldName)
                .thenRunAsync(() -> {
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
                })
                .exceptionally(e -> {
                    plugin.debug("Exception in deleting world: " + worldName + " - " + e.getMessage());
                    future.completeExceptionally(e);
                    return null;
                });
        return future;
    }

    private void copyTemplateWorld(String worldName) throws IOException {
        Path sourceDirectory = plugin.getDataFolder().toPath().resolve("template/skyblock");
        Path targetDirectory = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
        copyDirectory(sourceDirectory, targetDirectory);
    }

    private CompletableFuture<Void> loadWorldToBukkit(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                WorldCreator worldCreator = new WorldCreator(worldName);
                worldCreator.generator(new VoidGenerator());
                Bukkit.createWorld(worldCreator);
                plugin.debug("World created in Bukkit: " + worldName);
                future.complete(null);
            } else {
                plugin.debug("World already loaded in Bukkit: " + worldName);
                future.completeExceptionally(new IllegalStateException("World already loaded: " + worldName));
            }
            future.complete(null);
        });
        return future;
    }

    private CompletableFuture<Void> unloadWorldFromBukkit(String worldName) {
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

    private void deleteDirectory(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
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

    private void removePlayersFromWorld(World world) {
        World safeWorld = Bukkit.getServer().getWorlds().get(0);
        for (Player player : world.getPlayers()) {
            player.teleport(safeWorld.getSpawnLocation());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
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