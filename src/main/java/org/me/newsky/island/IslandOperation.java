package org.me.newsky.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.world.WorldHandler;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class IslandOperation {

    private final NewSky plugin;
    private final WorldHandler worldHandler;
    private final TeleportManager teleportManager;

    public IslandOperation(NewSky plugin, WorldHandler worldHandler, TeleportManager teleportManager) {
        this.plugin = plugin;
        this.worldHandler = worldHandler;
        this.teleportManager = teleportManager;
    }

    public CompletableFuture<String> updateWorldList() {
        return CompletableFuture.supplyAsync(() -> {
            File worldContainer = plugin.getServer().getWorldContainer();
            File[] files = worldContainer.listFiles();
            return Optional.ofNullable(files).stream().flatMap(Arrays::stream).filter(File::isDirectory).map(File::getName).filter(name -> name.startsWith("island-")).collect(Collectors.joining(","));
        });
    }

    public CompletableFuture<Void> createWorld(String worldName) {
        return worldHandler.createWorld(worldName);
    }

    public CompletableFuture<Void> loadWorld(String worldName) {
        return worldHandler.loadWorld(worldName);
    }

    public CompletableFuture<Void> unloadWorld(String worldName) {
        return worldHandler.unloadWorld(worldName);
    }

    public CompletableFuture<Void> deleteWorld(String worldName) {
        return worldHandler.deleteWorld(worldName);
    }

    public CompletableFuture<Void> teleportToWorld(String worldName, String playerName, String locationString) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Parse location components
        UUID playerUuid = UUID.fromString(playerName);
        String[] parts = locationString.split(",");
        double x = Double.parseDouble(parts[0]);
        double y = Double.parseDouble(parts[1]);
        double z = Double.parseDouble(parts[2]);
        float yaw = Float.parseFloat(parts[3]);
        float pitch = Float.parseFloat(parts[4]);
        Location location = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);

        Runnable teleportLogic = () -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.teleport(location);
            } else {
                teleportManager.addPendingTeleport(playerUuid, location);
            }
            future.complete(null);
        };

        // Check if the world is loaded
        if (Bukkit.getWorld(worldName) == null) {
            plugin.debug("World " + worldName + " not loaded, loading now.");
            worldHandler.loadWorld(worldName).thenRun(teleportLogic);
        } else {
            teleportLogic.run();
        }

        return future;
    }
}
