package org.me.newsky.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.me.newsky.cache.Cache;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandAccessListener implements Listener {

    private final ConfigHandler config;
    private final Cache cache;

    public IslandAccessListener(ConfigHandler config, Cache cache) {
        this.config = config;
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        String worldName = event.getTo().getWorld().getName();
        if (!IslandUtils.isIslandWorld(worldName)) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isOp()) {
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(worldName);
        UUID playerUuid = player.getUniqueId();

        if (cache.isPlayerBanned(islandUuid, playerUuid) || (cache.isIslandLock(islandUuid) && !cache.getIslandPlayers(islandUuid).contains(playerUuid))) {
            event.setCancelled(true);
            player.teleportAsync(Bukkit.getServer().getWorlds().getFirst().getSpawnLocation());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), config.getLobbyCommand(player.getName()));
            player.sendMessage(cache.isPlayerBanned(islandUuid, playerUuid) ? config.getPlayerBannedMessage() : config.getIslandLockedMessage());
        }
    }
}
