package org.me.newsky.event;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

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
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        victim.getWorld();
        if (!victim.getWorld().getName().startsWith("island-")) {
            return;
        }

        UUID islandUuid = UUID.fromString(victim.getWorld().getName().substring(7));

        if (!cacheHandler.getIslandPvp(islandUuid)) {
            event.setCancelled(true);
            attacker.sendMessage(config.getIslandPvpDisabledMessage());
        }
    }
}
