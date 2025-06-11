package org.me.newsky.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

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
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        if (!IslandUtils.isIslandWorld(event.getTo().getWorld().getName())) {
            return;
        }

        Player player = event.getPlayer();

        int centerX = 0;
        int centerZ = 0;
        int minX = centerX - (islandSize / 2) - bufferSize;
        int maxX = centerX + (islandSize / 2) + bufferSize;
        int minZ = centerZ - (islandSize / 2) - bufferSize;
        int maxZ = centerZ + (islandSize / 2) + bufferSize;

        if (event.getTo().getBlockX() < minX || event.getTo().getBlockX() > maxX || event.getTo().getBlockZ() < minZ || event.getTo().getBlockZ() > maxZ) {
            event.setCancelled(true);
            player.sendMessage(config.getCannotLeaveIslandBoundaryMessage());
            plugin.debug(getClass().getSimpleName(), "Player " + player.getName() + " tried to leave island boundary.");
        }
    }
}
