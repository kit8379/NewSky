package org.me.newsky.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class WorldHandler {

    private final NewSky plugin;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> worldOperations;

    public WorldHandler(NewSky plugin) {
        this.plugin = plugin;
        this.worldOperations = new ConcurrentHashMap<>();
    }

    public CompletableFuture<Void> createWorld(String worldName) {
        return worldOperations.computeIfAbsent(worldName, key -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            CompletableFuture.runAsync(() -> {
                        try {
                            copyTemplateWorld(worldName);
                            future.complete(null);
                        } catch (IOException e) {
                            future.completeExceptionally(e);
                        }
                    }).thenCompose(aVoid -> loadWorldToBukkit(worldName))
                    .thenAccept(aVoid -> future.complete(null))
                    .exceptionally(e -> {
                        future.completeExceptionally(e);
                        return null;
                    });
            return future.whenComplete((aVoid, throwable) -> worldOperations.remove(worldName));
        });
    }

    public CompletableFuture<Void> loadWorld(String worldName) {
        return worldOperations.computeIfAbsent(worldName, key -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            loadWorldToBukkit(worldName)
                    .thenRun(() -> future.complete(null))
                    .exceptionally(e -> {
                        future.completeExceptionally(e);
                        return null;
                    });
            return future.whenComplete((aVoid, throwable) -> worldOperations.remove(worldName));
        });
    }

    public CompletableFuture<Void> unloadWorld(String worldName) {
        return worldOperations.computeIfAbsent(worldName, key -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            unloadWorldFromBukkit(worldName)
                    .thenRun(() -> future.complete(null))
                    .exceptionally(e -> {
                        future.completeExceptionally(e);
                        return null;
                    });
            return future.whenComplete((aVoid, throwable) -> worldOperations.remove(worldName));
        });
    }

    public CompletableFuture<Void> deleteWorld(String worldName) {
        return worldOperations.computeIfAbsent(worldName, key -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            unloadWorldFromBukkit(worldName)
                    .thenRunAsync(() -> {
                        try {
                            Path worldDirectory = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
                            deleteDirectory(worldDirectory);
                            future.complete(null);
                        } catch (IOException e) {
                            future.completeExceptionally(e);
                        }
                    })
                    .exceptionally(e -> {
                        future.completeExceptionally(e);
                        return null;
                    });
            return future.whenComplete((aVoid, throwable) -> worldOperations.remove(worldName));
        });
    }

    private void copyTemplateWorld(String worldName) throws IOException {
        Path sourceDirectory = plugin.getDataFolder().toPath().resolve("template/skyblock");
        Path targetDirectory = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
        copyDirectory(sourceDirectory, targetDirectory);
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

    private CompletableFuture<Void> loadWorldToBukkit(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                Bukkit.createWorld(new WorldCreator(worldName).generator(new VoidGenerator()));
            } else {
                throw new IllegalStateException("World already loaded: " + worldName);
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

    private void deleteDirectory(Path path) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    private void removePlayersFromWorld(World world) {
        World safeWorld = Bukkit.getServer().getWorlds().get(0);
        for (Player player : world.getPlayers()) {
            player.teleport(safeWorld.getSpawnLocation());
        }
    }
}
