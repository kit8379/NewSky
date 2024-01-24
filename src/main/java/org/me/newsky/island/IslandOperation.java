package org.me.newsky.island;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.teleport.TeleportManager;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class IslandOperation {

    private final NewSky plugin;
    private final MVWorldManager mvWorldManager;
    private final TeleportManager teleportManager;

    public IslandOperation(NewSky plugin, MVWorldManager mvWorldManager, CacheHandler cacheHandler, TeleportManager teleportManager) {
        this.plugin = plugin;
        this.mvWorldManager = mvWorldManager;
        this.teleportManager = teleportManager;
    }

    public CompletableFuture<String> updateWorldList() {
        CompletableFuture<String> updateFuture = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                File worldContainer = plugin.getServer().getWorldContainer();
                File[] files = worldContainer.listFiles();
                if (files != null) {
                    String result = Arrays.stream(files)
                            .filter(File::isDirectory)
                            .map(File::getName)
                            .filter(name -> name.startsWith("island-"))
                            .collect(Collectors.joining(","));
                    updateFuture.complete(result); // Manually completing the future with the result
                }
            } catch (Exception e) {
                updateFuture.completeExceptionally(e); // Completing the future exceptionally in case of an error
            }
        });

        return updateFuture;
    }


    public CompletableFuture<Void> createWorld(String worldName) {
        CompletableFuture<Void> createFuture = new CompletableFuture<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            String generatorName = "VoidGen";
            World.Environment environment = World.Environment.NORMAL;
            WorldType worldType = WorldType.NORMAL;
            mvWorldManager.addWorld(worldName, environment, null, worldType, true, generatorName, false);
            createFuture.complete(null);  // Completes the future after the task
        });

        return createFuture;
    }


    public CompletableFuture<Void> deleteWorld(String worldName) {
        CompletableFuture<Void> deleteFuture = new CompletableFuture<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            mvWorldManager.unloadWorld(worldName);
            mvWorldManager.deleteWorld(worldName);
            deleteFuture.complete(null);
        });

        return deleteFuture;
    }


    public CompletableFuture<Void> teleportToWorld(String worldName, String playerName, String locationString) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Asynchronous task to fetch island spawn
        CompletableFuture.runAsync(() -> {
            UUID worldUuid = UUID.fromString(worldName.replace("island-", ""));
            UUID playerUuid = UUID.fromString(playerName);

            String[] parts = locationString.split(",");
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            float yaw = Float.parseFloat(parts[3]);
            float pitch = Float.parseFloat(parts[4]);

            // Switching back to the main thread to interact with the Minecraft world
            Bukkit.getScheduler().runTask(plugin, () -> {
                mvWorldManager.loadWorld(worldName);
                Location location = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null) {
                    player.teleport(location);
                } else {
                    teleportManager.addPendingTeleport(playerUuid, location);
                }
                future.complete(null);
            });
        }).exceptionally(e -> {
            future.completeExceptionally(e);
            return null;
        });

        return future;
    }
}

