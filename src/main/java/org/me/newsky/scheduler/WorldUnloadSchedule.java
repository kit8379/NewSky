package org.me.newsky.scheduler;

import org.me.newsky.NewSky;
import org.me.newsky.world.WorldHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WorldUnloadSchedule {

    private final NewSky plugin;
    private final WorldHandler worldHandler;
    private final long worldUnloadInterval;
    private final Map<String, Long> inactiveWorlds = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public WorldUnloadSchedule(NewSky plugin, WorldHandler worldHandler) {
        this.plugin = plugin;
        this.worldHandler = worldHandler;
        this.worldUnloadInterval = TimeUnit.SECONDS.toMillis(120);
    }

    public void startWorldUnloadTask() {
        scheduler.scheduleWithFixedDelay(this::checkAndUnloadWorlds, 0, worldUnloadInterval, TimeUnit.MILLISECONDS);
    }

    public void stopWorldUnloadTask() {
        scheduler.shutdown();
    }

    private void checkAndUnloadWorlds() {
        plugin.debug("Checking for inactive island worlds...");
        long currentTime = System.currentTimeMillis();
        plugin.getServer().getWorlds().forEach(world -> {
            if (world.getName().startsWith("island-") && world.getPlayers().isEmpty()) {
                long inactiveTime = inactiveWorlds.getOrDefault(world.getName(), currentTime);
                if (currentTime - inactiveTime > worldUnloadInterval) {
                    worldHandler.unloadWorld(world.getName()).thenRun(() -> {
                        plugin.debug("Unloaded inactive island world: " + world.getName());
                        inactiveWorlds.remove(world.getName());
                        plugin.debug("Removed inactive world from tracking: " + world.getName());
                    });
                } else {
                    inactiveWorlds.put(world.getName(), currentTime);
                }
            } else {
                inactiveWorlds.remove(world.getName());
            }
        });
        plugin.debug("Finished checking for inactive island worlds.");
    }
}
