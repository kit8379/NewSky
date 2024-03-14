package org.me.newsky.island.post;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.world.WorldHandler;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PostIslandHandler {

    private final NewSky plugin;
    private final WorldHandler worldHandler;
    private final TeleportManager teleportManager;

    public PostIslandHandler(NewSky plugin, WorldHandler worldHandler, TeleportManager teleportManager) {
        this.plugin = plugin;
        this.worldHandler = worldHandler;
        this.teleportManager = teleportManager;
    }

    public CompletableFuture<String> updateWorldList() {
        CompletableFuture<String> future = new CompletableFuture<>();
        Set<String> worldNames = Bukkit.getWorlds().stream().map(World::getName).filter(name -> name.startsWith("island-")).collect(Collectors.toSet());
        String worldList = String.join(",", worldNames);
        future.complete(worldList);
        return future;
    }


    public CompletableFuture<Void> createWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        worldHandler.createWorld(worldName).thenAcceptAsync(aVoid -> {
            // Do something here in the future
            future.complete(null);
        });

        return future;
    }

    public CompletableFuture<Void> loadWorld(String worldName) {
        return worldHandler.loadWorld(worldName);
    }

    public CompletableFuture<Void> unloadWorld(String worldName) {
        return worldHandler.unloadWorld(worldName);
    }

    public CompletableFuture<Void> deleteWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        worldHandler.deleteWorld(worldName).thenAcceptAsync(aVoid -> {
            // Do something here in the future
            future.complete(null);
        });

        return future;
    }

    public CompletableFuture<Void> teleportToWorld(String worldName, String playerName, String locationString) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        worldHandler.loadWorld(worldName).thenAcceptAsync(aVoid -> {
            UUID playerUuid = UUID.fromString(playerName);
            String[] parts = locationString.split(",");
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            float yaw = Float.parseFloat(parts[3]);
            float pitch = Float.parseFloat(parts[4]);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Location location = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null) {
                    player.teleport(location);
                    future.complete(null);
                } else {
                    teleportManager.addPendingTeleport(playerUuid, location);
                    future.complete(null);
                }
            });
        });

        return future;
    }
}
