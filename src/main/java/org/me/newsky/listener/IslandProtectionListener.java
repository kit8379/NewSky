package org.me.newsky.listener;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryHolder;
import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandProtectionListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final Cache cache;
    private final int islandSize;

    public IslandProtectionListener(NewSky plugin, ConfigHandler config, Cache cache) {
        this.plugin = plugin;
        this.config = config;
        this.cache = cache;
        this.islandSize = config.getIslandSize();
    }

    private boolean isInsideIslandBoundary(Location location) {
        if (location.getWorld() == null || !IslandUtils.isIslandWorld(location.getWorld().getName())) {
            return true;
        }

        int x = location.getBlockX();
        int z = location.getBlockZ();
        int half = islandSize / 2;

        return x >= -half && x <= (half - 1) && z >= -half && z <= (half - 1);
    }

    private boolean canPlayerEdit(Player player, Location location) {
        if (!isInsideIslandBoundary(location)) {
            return false;
        }
        if (player.isOp()) {
            return true;
        }
        UUID islandUuid = IslandUtils.nameToUUID(location.getWorld().getName());
        UUID playerUuid = player.getUniqueId();
        return cache.getIslandPlayers(islandUuid).contains(playerUuid) || cache.isPlayerCooped(islandUuid, playerUuid);
    }

    private void deny(Player player) {
        player.sendMessage(config.getCannotEditIslandMessage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!canPlayerEdit(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            deny(event.getPlayer());
            plugin.debug("IslandProtectionListener", "Player " + event.getPlayer().getName() + " tried to break a block in a protected area: " + event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!canPlayerEdit(event.getPlayer(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            deny(event.getPlayer());
            plugin.debug("IslandProtectionListener", "Player " + event.getPlayer().getName() + " tried to place a block in a protected area: " + event.getBlockPlaced().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && !canPlayerEdit(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            deny(event.getPlayer());
            plugin.debug("IslandProtectionListener", "Player " + event.getPlayer().getName() + " tried to interact with a block in a protected area: " + event.getClickedBlock().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketUse(PlayerBucketEmptyEvent event) {
        if (!canPlayerEdit(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            deny(event.getPlayer());
            plugin.debug("IslandProtectionListener", "Player " + event.getPlayer().getName() + " tried to use a bucket in a protected area: " + event.getBlock().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFluidSpread(BlockFromToEvent event) {
        if (!isInsideIslandBoundary(event.getToBlock().getLocation())) {
            event.setCancelled(true);
            plugin.debug("IslandProtectionListener", "Fluid spread blocked in a protected area: " + event.getToBlock().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (!isInsideIslandBoundary(event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.debug("IslandProtectionListener", "Block formation blocked in a protected area: " + event.getBlock().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            Block toBlock = block.getRelative(event.getDirection());
            if (!isInsideIslandBoundary(toBlock.getLocation())) {
                event.setCancelled(true);
                plugin.debug("IslandProtectionListener", "Piston extension blocked in a protected area: " + toBlock.getLocation());
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            Block toBlock = block.getRelative(event.getDirection());
            if (!isInsideIslandBoundary(toBlock.getLocation())) {
                event.setCancelled(true);
                plugin.debug("IslandProtectionListener", "Piston retraction blocked in a protected area: " + toBlock.getLocation());
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobTrample(EntityChangeBlockEvent event) {
        if (!isInsideIslandBoundary(event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.debug("IslandProtectionListener", "Mob trample blocked in a protected area: " + event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player damager = null;
        if (event.getDamager() instanceof Player p) {
            damager = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            damager = p;
        }

        if (damager == null) {
            return;
        }

        if (event.getEntity() instanceof Player || event.getEntity() instanceof Monster) {
            return;
        }

        if (!canPlayerEdit(damager, event.getEntity().getLocation())) {
            event.setCancelled(true);
            deny(damager);
            plugin.debug("IslandProtectionListener", "Player " + damager.getName() + " tried to damage an entity in a protected area: " + event.getEntity().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpenInventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BlockState state) {
            if (!canPlayerEdit(player, state.getBlock().getLocation())) {
                event.setCancelled(true);
                deny(player);
                plugin.debug("IslandProtectionListener", "Player " + player.getName() + " tried to open an inventory in a protected area: " + state.getBlock().getLocation());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFishingRodPull(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && event.getCaught() != null) {
            if (!canPlayerEdit(event.getPlayer(), event.getCaught().getLocation())) {
                event.setCancelled(true);
                deny(event.getPlayer());
                plugin.debug("IslandProtectionListener", "Player " + event.getPlayer().getName() + " tried to pull an entity with a fishing rod in a protected area: " + event.getCaught().getLocation());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if ((event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) && !canPlayerEdit(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
            deny(event.getPlayer());
            plugin.debug("IslandProtectionListener", "Player " + event.getPlayer().getName() + " tried to teleport to a protected area: " + event.getTo());
        }
    }
}
