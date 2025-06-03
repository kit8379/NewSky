package org.me.newsky.listener;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

public class IslandBoundaryListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final int islandSize;
    private final int bufferSize;

    public IslandBoundaryListener(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
        this.islandSize = config.getIslandSize();
        this.bufferSize = config.getBufferSize();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();

        if (to.getWorld() == null) {
            return;
        }

        int centerX = 0;
        int centerZ = 0;
        int minX = centerX - (islandSize / 2) - bufferSize;
        int maxX = centerX + (islandSize / 2) + bufferSize;
        int minZ = centerZ - (islandSize / 2) - bufferSize;
        int maxZ = centerZ + (islandSize / 2) + bufferSize;

        if (to.getBlockX() < minX || to.getBlockX() > maxX || to.getBlockZ() < minZ || to.getBlockZ() > maxZ) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getCannotLeaveIslandBoundaryMessage());
            plugin.debug(getClass().getSimpleName(), "Player " + event.getPlayer().getName() + " tried to leave island boundary.");
        }
    }
}
