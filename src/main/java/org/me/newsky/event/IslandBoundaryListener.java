package org.me.newsky.event;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class IslandBoundaryListener implements Listener {

    private static final int ISLAND_SIZE = 100;
    private static final int BUFFER_SIZE = 10;

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || !to.getWorld().getName().startsWith("island-")) {
            return;
        }

        int centerX = 0;
        int centerZ = 0;

        int minX = centerX - (ISLAND_SIZE / 2) - BUFFER_SIZE;
        int maxX = centerX + (ISLAND_SIZE / 2) + BUFFER_SIZE;
        int minZ = centerZ - (ISLAND_SIZE / 2) - BUFFER_SIZE;
        int maxZ = centerZ + (ISLAND_SIZE / 2) + BUFFER_SIZE;

        if (to.getBlockX() < minX || to.getBlockX() > maxX || to.getBlockZ() < minZ || to.getBlockZ() > maxZ) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot leave the island area.");
        }
    }
}
