package org.me.newsky.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUUIDUtils;

import java.util.UUID;

public class IslandPvPListener implements Listener {

    private final ConfigHandler config;
    private final CacheHandler cacheHandler;

    public IslandPvPListener(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!victim.getWorld().getName().startsWith("island-")) {
            return;
        }

        UUID islandUuid = IslandUUIDUtils.nameToUUID(victim.getWorld().getName());

        if (!cacheHandler.getIslandPvp(islandUuid)) {
            event.setCancelled(true);
            attacker.sendMessage(config.getIslandPvpDisabledMessage());
        }
    }
}
