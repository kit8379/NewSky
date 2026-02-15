package org.me.newsky.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandAccessListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;

    public IslandAccessListener(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        checkAccess(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        checkAccess(event.getPlayer());
    }

    private void checkAccess(Player player) {
        if (player.isOp()) {
            return;
        }

        if (!IslandUtils.isIslandWorld(player.getWorld().getName())) {
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(player.getWorld().getName());
        UUID playerUuid = player.getUniqueId();

        boolean banned = plugin.getApi().isPlayerBanned(islandUuid, playerUuid);
        boolean locked = plugin.getApi().isIslandLock(islandUuid) && !plugin.getApi().getIslandPlayers(islandUuid).contains(playerUuid);

        if (banned || locked) {
            player.teleportAsync(Bukkit.getServer().getWorlds().getFirst().getSpawnLocation());
            plugin.getApi().lobby(playerUuid);
            plugin.getApi().sendPlayerMessage(playerUuid, banned ? config.getPlayerBannedMessage() : config.getIslandLockedMessage());
            plugin.debug("IslandAccessListener", "Player " + player.getName() + " attempted to access island " + islandUuid + " but was denied access.");
        }
    }
}