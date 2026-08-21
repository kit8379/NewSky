package org.me.newsky.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.me.newsky.NewSky;
import org.me.newsky.snapshot.IslandSnapshot;
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
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
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

    /**
     * PvP is player-versus-player however the damage travels: a direct hit, or an arrow, trident,
     * splash potion or other projectile shot by a player. Only resolving the direct damager would
     * let ranged attacks walk straight past a PvP-off island.
     */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }

        return null;
    }
}
