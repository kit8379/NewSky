package org.me.newsky.event;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.me.newsky.config.ConfigHandler;

public class IslandBoundaryListener implements Listener {

    private final ConfigHandler config;
    private final int islandSize;
    private final int bufferSize;
    private final int centerX;
    private final int centerZ;

    public IslandBoundaryListener(ConfigHandler config) {
        this.config = config;
        this.islandSize = config.getIslandSize();
        this.bufferSize = config.getBufferSize();
        this.centerX = 0;
        this.centerZ = 0;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() - to.getBlockX() == 0 && from.getBlockZ() - to.getBlockZ() == 0)) {
            return;
        }

        if (to.getWorld() == null || !to.getWorld().getName().startsWith("island-")) {
            return;
        }

        int minX = centerX - (islandSize / 2) - bufferSize;
        int maxX = centerX + (islandSize / 2) + bufferSize;
        int minZ = centerZ - (islandSize / 2) - bufferSize;
        int maxZ = centerZ + (islandSize / 2) + bufferSize;

        if (to.getBlockX() < minX || to.getBlockX() > maxX || to.getBlockZ() < minZ || to.getBlockZ() > maxZ) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getCannotLeaveIslandBoundaryMessage());
        }
    }
}