package org.me.newsky.world.normal;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.world.WorldHandler;
import org.me.newsky.world.normal.generator.VoidGenerator;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;

public abstract class NormalWorldHandler extends WorldHandler {

    public NormalWorldHandler(NewSky plugin, ConfigHandler config) {
        super(plugin, config);
    }

    protected void copyTemplateWorld(String worldName) throws IOException {
        Path sourceDirectory = plugin.getDataFolder().toPath().resolve("template/" + config.getTemplateWorldName());
        Path targetDirectory = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
        copyDirectory(sourceDirectory, targetDirectory);
    }

    protected CompletableFuture<Void> loadWorldToBukkit(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (isWorldLoaded(worldName)) {
            future.complete(null);
            return future;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                WorldCreator worldCreator = new WorldCreator(worldName).generator(new VoidGenerator(config.getIslandSpawnX(), config.getIslandSpawnY(), config.getIslandSpawnZ(), config.getIslandSpawnYaw(), config.getIslandSpawnPitch()));
                Bukkit.createWorld(worldCreator);
                future.complete(null);
            } else {
                future.completeExceptionally(new IllegalStateException(config.getIslandAlreadyLoadedMessage()));
            }
        });

        return future;
    }

    protected void copyDirectory(Path source, Path target) throws IOException {
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

    protected void deleteDirectory(Path path) throws IOException {
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


    public CompletableFuture<Void> createWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                copyTemplateWorld(worldName);
            } catch (IOException e) {
                future.completeExceptionally(e);
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

    public abstract CompletableFuture<Void> loadWorld(String worldName);

    public abstract CompletableFuture<Void> unloadWorld(String worldName);

    public abstract CompletableFuture<Void> deleteWorld(String worldName);
}
