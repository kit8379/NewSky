package org.me.newsky.island;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;

public class IslandOperation {

    private final NewSky plugin;

    public IslandOperation(NewSky plugin) {
        this.plugin = plugin;
    }

    public void createWorldOperation(String worldName) {
        String generatorName = "VoidGen"; // Replace with your plugin's name
        World.Environment environment = World.Environment.NORMAL; // or NETHER, or THE_END
        WorldType worldType = WorldType.NORMAL; // or any other type you wish

        if (mvWorldManager.addWorld(worldName, environment, null, worldType, true, generatorName, false)) {
            Bukkit.getLogger().info("World created successfully!");
        } else {
            Bukkit.getLogger().severe("Failed to create world!");
        }
    }

    public void loadWorldOperation(String worldName) {
        if (mvWorldManager.loadWorld(worldName)) {
            Bukkit.getLogger().info("World loaded successfully!");
        } else {
            Bukkit.getLogger().severe("Failed to load world!");
        }
    }

    public void unloadWorldOperation(String worldName) {
        if (mvWorldManager.unloadWorld(worldName)) {
            Bukkit.getLogger().info("World unloaded successfully!");
        } else {
            Bukkit.getLogger().severe("Failed to unload world!");
        }
    }

    public void deleteWorldOperation(String worldName) {
        boolean successUnload = mvWorldManager.unloadWorld(worldName);
        boolean successDelete = mvWorldManager.deleteWorld(worldName);
        if (successUnload && successDelete) {
            Bukkit.getLogger().info("World deleted successfully!");
        } else {
            Bukkit.getLogger().severe("Failed to delete world!");
        }
    }


    public void teleportToSpawnOperation(String worldName, String playerName) {
        World targetWorld = Bukkit.getWorld(worldName);
        Player player = Bukkit.getPlayer(playerName);
        if (targetWorld != null && player != null) {
            player.teleport(targetWorld.getSpawnLocation());
        }
    }
}
