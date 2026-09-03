package org.me.newsky.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.potion.PotionType;
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

    private UUID resolvePlayerUuid(Entity entity) {
        if (entity instanceof Player player) {
            return player.getUniqueId();
        }

        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player.getUniqueId();
        }

        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player.getUniqueId();
        }

        if (entity instanceof Tameable tameable) {
            return tameable.getOwnerUniqueId();
        }

        return null;
    }

    private boolean isPvpAllowed(Player victim) {
        UUID islandUuid = IslandUtils.parseIslandUuid(victim.getWorld().getName());
        if (islandUuid == null) {
            return true;
        }

        Island island = islandSnapshot.get(islandUuid);
        return island != null && island.isPvp();
    }

    private boolean isBlockedTarget(Player victim, UUID attackerUuid) {
        return !victim.getUniqueId().equals(attackerUuid) && !isPvpAllowed(victim);
    }

    private boolean isHarmful(Iterable<PotionEffect> effects) {
        for (PotionEffect effect : effects) {
            if (effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                return true;
            }
        }

        return false;
    }

    private boolean isHarmful(AreaEffectCloud cloud) {
        if (isHarmful(cloud.getCustomEffects())) {
            return true;
        }

        PotionType basePotionType = cloud.getBasePotionType();
        return basePotionType != null && isHarmful(basePotionType.getPotionEffects());
    }

    private void notifyAttacker(UUID attackerUuid) {
        Player attacker = Bukkit.getPlayer(attackerUuid);
        if (attacker != null) {
            attacker.sendMessage(config.getIslandPvpDisabledMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        UUID attackerUuid = resolvePlayerUuid(event.getDamager());
        if (attackerUuid == null || attackerUuid.equals(victim.getUniqueId())) {
            return;
        }

        if (isPvpAllowed(victim)) {
            return;
        }

        event.setCancelled(true);
        notifyAttacker(attackerUuid);
        plugin.debug("IslandPvPListener", "Cancelled PvP from " + attackerUuid + " against " + victim.getName() + " in island world: " + victim.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        if (!(potion.getShooter() instanceof Player attacker) || !isHarmful(potion.getEffects())) {
            return;
        }

        boolean blocked = false;
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Player victim && isBlockedTarget(victim, attacker.getUniqueId())) {
                event.setIntensity(victim, 0.0);
                blocked = true;
            }
        }

        if (blocked) {
            attacker.sendMessage(config.getIslandPvpDisabledMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        if (!(cloud.getSource() instanceof Player attacker) || !isHarmful(cloud)) {
            return;
        }

        int affectedBefore = event.getAffectedEntities().size();
        event.getAffectedEntities().removeIf(entity ->
                entity instanceof Player victim && isBlockedTarget(victim, attacker.getUniqueId()));

        if (event.getAffectedEntities().size() < affectedBefore) {
            attacker.sendMessage(config.getIslandPvpDisabledMessage());
        }
    }
}
