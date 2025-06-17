package org.me.newsky.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandAccessListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final Cache cache;

    public IslandAccessListener(NewSky plugin, ConfigHandler config, Cache cache) {
        this.plugin = plugin;
        this.config = config;
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        checkAccess(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        checkAccess(event.getPlayer());
    }

    private void checkAccess(Player player) {
        if (player.isOp()) {
            return;
        }

        String worldName = player.getWorld().getName();
        if (!IslandUtils.isIslandWorld(worldName)) {
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(worldName);
        UUID playerUuid = player.getUniqueId();

        boolean banned = cache.isPlayerBanned(islandUuid, playerUuid);
        boolean locked = cache.isIslandLock(islandUuid) && !cache.getIslandPlayers(islandUuid).contains(playerUuid);

        if (banned || locked) {
            player.teleportAsync(Bukkit.getServer().getWorlds().getFirst().getSpawnLocation());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), config.getLobbyCommand(player.getName()));
            player.sendMessage(banned ? config.getPlayerBannedMessage() : config.getIslandLockedMessage());
            plugin.debug("IslandAccessListener", "Player " + player.getName() + " attempted to access island " + islandUuid + " but was denied access.");
        }
    }
}