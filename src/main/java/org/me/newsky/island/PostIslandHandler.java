package org.me.newsky.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.util.IslandUUIDUtils;
import org.me.newsky.world.WorldHandler;

import java.util.UUID;

public class PostIslandHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final WorldHandler worldHandler;
    private final TeleportManager teleportManager;

    public PostIslandHandler(NewSky plugin, ConfigHandler configHandler, CacheHandler cacheHandler, WorldHandler worldHandler, TeleportManager teleportManager) {
        this.plugin = plugin;
        this.config = configHandler;
        this.cacheHandler = cacheHandler;
        this.worldHandler = worldHandler;
        this.teleportManager = teleportManager;
    }

    public void createIsland(UUID islandUuid, UUID playerUuid, String spawnLocation) {
        String islandName = IslandUUIDUtils.UUIDToName(islandUuid);

        worldHandler.createWorld(islandName).thenRun(() -> {
            cacheHandler.createIsland(islandUuid);
            cacheHandler.updateIslandPlayer(islandUuid, playerUuid, "owner");
            cacheHandler.updateHomePoint(islandUuid, playerUuid, "default", spawnLocation);
            cacheHandler.updateIslandLoadedServer(islandUuid, config.getServerName());
            plugin.debug("Created island " + islandUuid + " in the cache");
        });
    }

    public void deleteIsland(UUID islandUuid) {
        String islandName = IslandUUIDUtils.UUIDToName(islandUuid);

        worldHandler.deleteWorld(islandName).thenRun(() -> {
            cacheHandler.deleteIsland(islandUuid);
            cacheHandler.removeIslandLoadedServer(islandUuid);
            plugin.debug("Deleted island " + islandName + " from the cache");
        });
    }

    public void loadIsland(UUID islandUuid) {
        String islandName = IslandUUIDUtils.UUIDToName(islandUuid);

        worldHandler.loadWorld(islandName).thenRun(() -> {
            cacheHandler.updateIslandLoadedServer(islandUuid, config.getServerName());
            plugin.debug("Loaded island " + islandName);
        });
    }

    public void unloadIsland(UUID islandUuid) {
        String islandName = IslandUUIDUtils.UUIDToName(islandUuid);

        worldHandler.unloadWorld(islandName).thenRun(() -> {
            cacheHandler.removeIslandLoadedServer(islandUuid);
            plugin.debug("Unloaded island " + islandName);
        });
    }

    public void teleportIsland(UUID islandUuid, UUID playerUuid, String teleportLocation) {
        String islandName = IslandUUIDUtils.UUIDToName(islandUuid);

        worldHandler.loadWorld(islandName).thenRun(() -> {
            cacheHandler.updateIslandLoadedServer(islandUuid, config.getServerName());

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
                } else {
                    teleportManager.addPendingTeleport(playerUuid, location);
                    // TODO: Send a broker message to send the player to this server
                }
            });
        });
    }

    public void lockIsland(UUID islandUuid) {
        String islandName = IslandUUIDUtils.UUIDToName(islandUuid);
        worldHandler.lockWorld(islandName);
    }
}
