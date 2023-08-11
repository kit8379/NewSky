package org.me.newsky.island;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;

public class IslandHandler {

    private final NewSky plugin;
    private final MVWorldManager mvWorldManager;

    public IslandHandler(NewSky plugin) {
        this.plugin = plugin;
        this.mvWorldManager = plugin.getMVWorldManager();

    }

    public void createWorld (String worldName) {
        String generatorName = "VoidGen"; // Replace with your plugin's name
        Environment environment = Environment.NORMAL; // or NETHER, or THE_END
        WorldType worldType = WorldType.NORMAL; // or any other type you wish

        if (mvWorldManager.addWorld(worldName, environment, null, worldType, true, generatorName, false)) {
            Bukkit.getLogger().info("World created successfully!");
        } else {
            Bukkit.getLogger().severe("Failed to create world!");
        }
    }


    public void loadWorld(String worldName) {
        if (mvWorldManager.loadWorld(worldName)) {
            Bukkit.getLogger().info("World loaded successfully!");
        } else {
            Bukkit.getLogger().severe("Failed to load world!");
        }
    }

    public void unloadWorld(String worldName) {
        if (mvWorldManager.unloadWorld(worldName)) {
            Bukkit.getLogger().info("World unloaded successfully!");
        } else {
            Bukkit.getLogger().severe("Failed to unload world!");
        }
    }

    public void deleteWorld(String worldName) {
        boolean successUnload = mvWorldManager.unloadWorld(worldName);
        boolean successDelete = mvWorldManager.deleteWorld(worldName);
        if (successUnload && successDelete) {
            Bukkit.getLogger().info("World deleted successfully!");
        } else {
            Bukkit.getLogger().severe("Failed to delete world!");
        }
    }

    public void teleportToSpawn(Player player, String worldName) {
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            Bukkit.getLogger().severe("World not found: " + worldName);
            return;
        }

        Location spawnLocation = world.getSpawnLocation();
        player.teleport(spawnLocation);
        Bukkit.getLogger().info(player.getName() + " has been teleported to the spawn of " + worldName + "!");
    }
}
