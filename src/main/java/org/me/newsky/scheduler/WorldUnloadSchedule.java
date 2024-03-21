package org.me.newsky.scheduler;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.world.WorldHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WorldUnloadSchedule {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final WorldHandler worldHandler;
    private final int unloadInterval;

    public WorldUnloadSchedule(NewSky plugin, ConfigHandler config, WorldHandler worldHandler) {
        this.plugin = plugin;
        this.config = config;
        this.worldHandler = worldHandler;
        this.unloadInterval = config.getWorldUnloadInterval();
    }

    public void start() {
        // Start the world unload schedule
    }

    public void stop() {
        // Stop the world unload schedule
    }

    // TODO: Implement the world unload logic
}
