package org.me.newsky.island.post;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.world.WorldHandler;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;
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

    // TODO: Optimize this method
    public CompletableFuture<String> updateWorldList() {
        CompletableFuture<String> future = new CompletableFuture<>();
        // Add loaded worlds that start with "island-"
        Set<String> worldNames = Bukkit.getWorlds().stream().map(World::getName).filter(name -> name.startsWith("island-")).collect(Collectors.toSet());

        // Add unloaded worlds (directories in the world container) that start with "island-"
        File worldContainer = Bukkit.getServer().getWorldContainer();
        worldNames.addAll(Arrays.stream(Optional.ofNullable(worldContainer.listFiles()).orElse(new File[0])).filter(File::isDirectory).map(File::getName).filter(name -> name.startsWith("island-") && Bukkit.getWorld(name) == null).collect(Collectors.toSet()));

        // Convert the set to a comma-separated string
        String worldList = String.join(",", worldNames);
        future.complete(worldList);

        return future;
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

        worldHandler.loadWorld(worldName).thenAccept(aVoid -> {
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
