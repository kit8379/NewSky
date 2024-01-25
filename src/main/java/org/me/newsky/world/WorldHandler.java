package org.me.newsky.world;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.me.newsky.NewSky;

public class WorldHandler {

    private final NewSky plugin;
    private final MVWorldManager mvWorldManager;

    public WorldHandler(NewSky plugin, MVWorldManager mvWorldManager) {
        this.plugin = plugin;
        this.mvWorldManager = mvWorldManager;
    }

    public void createWorld(String worldName) {
        mvWorldManager.addWorld(worldName, World.Environment.NORMAL, null, WorldType.NORMAL, true, "VoidGen", false);;
    }

    public void loadWorld(String worldName) {
        mvWorldManager.loadWorld(worldName);
    }

    public void unloadWorld(String worldName) {
        mvWorldManager.unloadWorld(worldName);
    }

    public void deleteWorld(String worldName) {
        mvWorldManager.deleteWorld(worldName);
    }

}
