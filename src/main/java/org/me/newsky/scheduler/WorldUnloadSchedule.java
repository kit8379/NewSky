package org.me.newsky.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.world.WorldHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WorldUnloadSchedule {

    private static final long MAX_INACTIVE_TIME = TimeUnit.MINUTES.toMillis(10);
    private final NewSky plugin;
    private final WorldHandler worldHandler;
    private final Map<String, Long> inactiveWorlds = new HashMap<>();
    private BukkitTask unloadTask;

    public WorldUnloadSchedule(NewSky plugin, WorldHandler worldHandler) {
        this.plugin = plugin;
        this.worldHandler = worldHandler;
    }

    public void startWorldUnloadTask() {
        unloadTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::checkAndUnloadWorlds, 0L, 1200L); // 1200L ticks = 1 minute
    }

    public void stopWorldUnloadTask() {
        if (unloadTask != null) {
            unloadTask.cancel();
        }
    }

    private void checkAndUnloadWorlds() {
        plugin.debug("Checking for inactive island worlds...");
        long currentTime = System.currentTimeMillis();
        Bukkit.getWorlds().forEach(world -> {
            if (world.getName().startsWith("island-") && world.getPlayers().isEmpty()) {
                long inactiveTime = inactiveWorlds.getOrDefault(world.getName(), currentTime);
                if (currentTime - inactiveTime > MAX_INACTIVE_TIME) {
                    worldHandler.unloadWorld(world.getName()).thenRun(() -> plugin.info("Unloaded inactive island world: " + world.getName()));
                    inactiveWorlds.remove(world.getName());
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
