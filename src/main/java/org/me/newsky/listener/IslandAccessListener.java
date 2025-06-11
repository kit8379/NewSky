package org.me.newsky.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandAccessListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;

    public IslandAccessListener(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler) {
        this.plugin = plugin;
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isOp()) {
            return;
        }

        String worldName = event.getTo().getWorld().getName();
        if (!IslandUtils.isIslandWorld(worldName)) {
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(worldName);
        UUID playerUuid = player.getUniqueId();

        if (cacheHandler.isPlayerBanned(islandUuid, playerUuid) || (cacheHandler.isIslandLock(islandUuid) && !cacheHandler.getIslandPlayers(islandUuid).contains(playerUuid))) {
            event.setCancelled(true);
            player.teleportAsync(Bukkit.getServer().getWorlds().getFirst().getSpawnLocation());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), config.getLobbyCommand(player.getName()));
            player.sendMessage(cacheHandler.isPlayerBanned(islandUuid, playerUuid) ? config.getPlayerBannedMessage() : config.getIslandLockedMessage());
            plugin.debug(getClass().getSimpleName(), "Player " + player.getName() + " was denied access to island " + islandUuid + " due to ban or lock.");
        }
    }
}
