package org.me.newsky.island.post;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.world.WorldHandler;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PostIslandHandler {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final WorldHandler worldHandler;
    private final TeleportManager teleportManager;

    public PostIslandHandler(NewSky plugin, CacheHandler cacheHandler, WorldHandler worldHandler, TeleportManager teleportManager) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
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


    public CompletableFuture<Void> createIsland(String islandName, String playerName, String spawnLocation) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        worldHandler.createWorld(islandName).thenAcceptAsync(aVoid -> {
            UUID islandUuid = UUID.fromString(islandName.substring(7));
            UUID playerUuid = UUID.fromString(playerName);

            // Create the island into the cache
            cacheHandler.createIsland(islandUuid);
            cacheHandler.addOrUpdateIslandPlayer(playerUuid, islandUuid, "owner");
            cacheHandler.addOrUpdateHomePoint(playerUuid, islandUuid, "default", spawnLocation);
            plugin.debug("Created island " + islandName + " in the cache");

            future.complete(null);
        });

        return future;
    }

    public CompletableFuture<Void> deleteIsland(String islandName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        worldHandler.deleteWorld(islandName).thenAcceptAsync(aVoid -> {
            UUID islandUuid = UUID.fromString(islandName.substring(7));

            // Delete the island from the cache
            cacheHandler.deleteIsland(islandUuid);
            plugin.debug("Deleted island " + islandName + " from the cache");

            future.complete(null);
        });

        return future;
    }

    public CompletableFuture<Void> loadIsland(String islandName) {
        return worldHandler.loadWorld(islandName);
    }

    public CompletableFuture<Void> unloadIsland(String islandName) {
        return worldHandler.unloadWorld(islandName);
    }



    public CompletableFuture<Void> teleportToIsland(String islandName, String playerName, String teleportLocation) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        worldHandler.loadWorld(islandName).thenAcceptAsync(aVoid -> {
            UUID playerUuid = UUID.fromString(playerName);

            String[] parts = teleportLocation.split(",");
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            float yaw = Float.parseFloat(parts[3]);
            float pitch = Float.parseFloat(parts[4]);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Location location = new Location(Bukkit.getWorld(islandName), x, y, z, yaw, pitch);
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

    public CompletableFuture<Void> lockIsland(String islandName) {
        return worldHandler.lockWorld(islandName);
    }
}
