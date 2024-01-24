package org.me.newsky.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.me.newsky.NewSky;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WorldUnloadHandler {

    private final NewSky plugin;
    private final MVWorldManager mvWorldManager;
    private BukkitTask unloadTask;
    private final Map<String, Long> inactiveWorlds = new HashMap<>();
    private static final long MAX_INACTIVE_TIME = TimeUnit.MINUTES.toMillis(10);

    public WorldUnloadHandler(NewSky plugin, MVWorldManager mvWorldManager) {
        this.plugin = plugin;
        this.mvWorldManager = mvWorldManager;
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
        long currentTime = System.currentTimeMillis();
        Bukkit.getWorlds().forEach(world -> {
            // Check if the world name starts with "island-"
            if (world.getName().startsWith("island-") && world.getPlayers().isEmpty()) {
                long inactiveTime = inactiveWorlds.getOrDefault(world.getName(), currentTime);
                if (currentTime - inactiveTime > MAX_INACTIVE_TIME) {
                    // Schedule the world unload on the main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        mvWorldManager.unloadWorld(world.getName());
                        plugin.getLogger().info("Unloaded inactive world: " + world.getName());
                    });
                    inactiveWorlds.remove(world.getName());
                } else {
                    inactiveWorlds.put(world.getName(), inactiveTime);
                }
            } else {
                inactiveWorlds.remove(world.getName());
            }
        });
    }
}
