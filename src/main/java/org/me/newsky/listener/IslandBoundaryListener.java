package org.me.newsky.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

public class IslandBoundaryListener implements Listener {

    private final ConfigHandler config;
    private final int islandSize;
    private final int bufferSize;

    public IslandBoundaryListener(ConfigHandler config) {
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
        int half = islandSize / 2;

        int minX = centerX - half;
        int maxX = centerX + half - 1;
        int minZ = centerZ - half;
        int maxZ = centerZ + half - 1;

        minX -= bufferSize;
        maxX += bufferSize;
        minZ -= bufferSize;
        maxZ += bufferSize;

        int toX = event.getTo().getBlockX();
        int toZ = event.getTo().getBlockZ();

        if (toX < minX || toX > maxX || toZ < minZ || toZ > maxZ) {
            event.setCancelled(true);
            player.sendMessage(config.getCannotLeaveIslandBoundaryMessage());
        }
    }
}