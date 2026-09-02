package org.me.newsky.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.me.newsky.NewSky;
import snapshot.IslandSnapshot;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.model.Island;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandPvPListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final IslandSnapshot islandSnapshot;

    public IslandPvPListener(NewSky plugin, ConfigHandler config, IslandSnapshot islandSnapshot) {
        this.plugin = plugin;
        this.config = config;
        this.islandSnapshot = islandSnapshot;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }

        UUID islandUuid = IslandUtils.parseIslandUuid(victim.getWorld().getName());
        if (islandUuid == null) {
            return;
        }

        Island island = islandSnapshot.get(islandUuid);
        if (island == null) {
            event.setCancelled(true);
            attacker.sendMessage(config.getIslandPvpDisabledMessage());
            return;
        }

        if (!island.isPvp()) {
            event.setCancelled(true);
            attacker.sendMessage(config.getIslandPvpDisabledMessage());
            plugin.debug("IslandPvPListener", "Cancelled PvP between " + attacker.getName() + " and " + victim.getName() + " in island world: " + victim.getWorld().getName());
        }
    }
}
