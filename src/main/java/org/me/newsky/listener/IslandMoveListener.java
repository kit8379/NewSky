package org.me.newsky.listener;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandMoveListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final int islandSize;
    private final int bufferSize;
    private final int centerX;
    private final int centerZ;

    public IslandMoveListener(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler) {
        this.plugin = plugin;
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.islandSize = config.getIslandSize();
        this.bufferSize = config.getBufferSize();
        this.centerX = 0;
        this.centerZ = 0;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Check if the player has admin permissions
        if (event.getPlayer().hasPermission("newsky.admin.bypass")) {
            plugin.debug(getClass().getSimpleName(), "Player " + event.getPlayer().getName() + " has admin permissions, bypassing island move checks.");
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() - to.getBlockX() == 0 && from.getBlockZ() - to.getBlockZ() == 0) {
            plugin.debug(getClass().getSimpleName(), "Player " + event.getPlayer().getName() + " did not move, skipping move checks.");
            return;
        }

        if (to.getWorld() == null || !IslandUtils.isIslandWorld(to.getWorld().getName())) {
            plugin.debug(getClass().getSimpleName(), "Player " + event.getPlayer().getName() + " is not in an island world, skipping move checks.");
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(to.getWorld().getName());
        UUID playerUuid = event.getPlayer().getUniqueId();

        // Check if the player is banned from the island
        if (cacheHandler.getPlayerBanned(islandUuid, playerUuid)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getPlayerBannedMessage());
            plugin.debug(getClass().getSimpleName(), "Player " + event.getPlayer().getName() + " is banned from island " + islandUuid);
            return;
        }

        // Check if the island is locked
        if (cacheHandler.getIslandLock(islandUuid) && !cacheHandler.getIslandPlayers(islandUuid).contains(playerUuid)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getIslandLockedMessage());
            plugin.debug(getClass().getSimpleName(), "Player " + event.getPlayer().getName() + " tried to enter locked island " + islandUuid);
            return;
        }

        // Check if the player is within the island boundary
        int minX = centerX - (islandSize / 2) - bufferSize;
        int maxX = centerX + (islandSize / 2) + bufferSize;
        int minZ = centerZ - (islandSize / 2) - bufferSize;
        int maxZ = centerZ + (islandSize / 2) + bufferSize;

        if (to.getBlockX() < minX || to.getBlockX() > maxX || to.getBlockZ() < minZ || to.getBlockZ() > maxZ) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getCannotLeaveIslandBoundaryMessage());
            plugin.debug(getClass().getSimpleName(), "Player " + event.getPlayer().getName() + " tried to leave island boundary of " + islandUuid);
        }
    }
}
