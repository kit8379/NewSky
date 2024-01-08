package org.me.newsky.island;

import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

import java.util.logging.Logger;

public class IslandHandler {

    private final Logger logger;
    private final String serverID;

    public IslandHandler(NewSky plugin, Logger logger, ConfigHandler config) {
        this.logger = logger;
        this.serverID = config.getServerName();
    }

    public void createIsland(String islandName) {

    }


    public void loadIsland(String islandName) {

    }

    public void unloadIsland(String islandName) {

    }

    public void deleteIsland(String islandName) {

    }

    public void teleportToIsland(Player player, String islandName) {

    }
}