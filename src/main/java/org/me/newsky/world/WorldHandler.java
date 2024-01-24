package org.me.newsky.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.me.newsky.NewSky;

public class WorldHandler {

    private final NewSky plugin;

    public WorldHandler(NewSky plugin) {
        this.plugin = plugin;
    }

    public void createWorld(String worldName) {
        WorldCreator creator = new WorldCreator(worldName);
        // creator.generator(new VoidWorldGenerator());
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.createWorld(creator));
    }


    public void loadWorld(String worldName) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (Bukkit.getWorld(worldName) == null) {
                Bukkit.createWorld(new WorldCreator(worldName));
            }
        });
    }

    public void unloadWorld(String worldName) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                Bukkit.unloadWorld(world, true);
            }
        });
    }
}
