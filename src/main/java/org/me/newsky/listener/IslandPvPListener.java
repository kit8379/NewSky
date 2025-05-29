package org.me.newsky.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandPvPListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;

    public IslandPvPListener(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler) {
        this.plugin = plugin;
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            plugin.debug(getClass().getSimpleName(), "Entity damage event is not between players, skipping.");
            return;
        }

        if (!IslandUtils.isIslandWorld(victim.getWorld().getName())) {
            plugin.debug(getClass().getSimpleName(), "Victim is not in an island world, skipping.");
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(victim.getWorld().getName());

        if (!cacheHandler.getIslandPvp(islandUuid)) {
            event.setCancelled(true);
            attacker.sendMessage(config.getIslandPvpDisabledMessage());
            plugin.debug(getClass().getSimpleName(), "PvP is disabled on island " + islandUuid + ", cancelling damage event.");
        }
    }
}
