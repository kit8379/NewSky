package org.me.newsky.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.InventoryHolder;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandProtectionListener implements Listener {

    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final int halfSize;

    public IslandProtectionListener(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.halfSize = config.getIslandSize() / 2;
    }

    private boolean canPlayerEdit(Player player, Location location) {
        if (player.hasPermission("newsky.admin.bypass")) {
            return true;
        }

        if (location.getWorld() == null || !IslandUtils.isIslandWorld(location.getWorld().getName())) {
            return true;
        }

        UUID islandUuid = IslandUtils.nameToUUID(location.getWorld().getName());
        int centerX = 0;
        int centerZ = 0;
        int minX = centerX - halfSize;
        int maxX = centerX + halfSize;
        int minZ = centerZ - halfSize;
        int maxZ = centerZ + halfSize;

        if (location.getBlockX() < minX || location.getBlockX() > maxX || location.getBlockZ() < minZ || location.getBlockZ() > maxZ)
            return false;

        return cacheHandler.getIslandPlayers(islandUuid).contains(player.getUniqueId());
    }

    private boolean isVisitor(Location location, Player player) {
        if (player.hasPermission("newsky.bypass.protection")) {
            return false;
        }

        if (location.getWorld() == null || !IslandUtils.isIslandWorld(location.getWorld().getName())) return false;

        UUID islandUuid = IslandUtils.nameToUUID(location.getWorld().getName());

        int centerX = 0;
        int centerZ = 0;
        int minX = centerX - halfSize;
        int maxX = centerX + halfSize;
        int minZ = centerZ - halfSize;
        int maxZ = centerZ + halfSize;

        if (location.getBlockX() < minX || location.getBlockX() > maxX || location.getBlockZ() < minZ || location.getBlockZ() > maxZ)
            return false;

        return !cacheHandler.getIslandPlayers(islandUuid).contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!canPlayerEdit(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getCannotEditIslandMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!canPlayerEdit(event.getPlayer(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getCannotEditIslandMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if ((event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) && event.getClickedBlock() != null && !canPlayerEdit(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getCannotEditIslandMessage());
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Material type = event.getClickedBlock().getType();
            if (type.name().endsWith("PRESSURE_PLATE")) return;

            if (isVisitor(event.getClickedBlock().getLocation(), event.getPlayer())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCropTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.FARMLAND) {
            if (isVisitor(event.getClickedBlock().getLocation(), event.getPlayer())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobTrample(EntityChangeBlockEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND && IslandUtils.isIslandWorld(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        if (isVisitor(entity.getLocation(), player)) {
            event.setCancelled(true);
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

        if (isVisitor(event.getEntity().getLocation(), damager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketUse(PlayerBucketEmptyEvent event) {
        if (isVisitor(event.getBlock().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFluidSpread(BlockFromToEvent event) {
        if (!IslandUtils.isIslandWorld(event.getBlock().getWorld().getName())) {
            return;
        }

        if (!event.getBlock().getWorld().equals(event.getToBlock().getWorld())) {
            return;
        }

        int x = event.getToBlock().getX();
        int z = event.getToBlock().getZ();

        int minX = -halfSize;
        int minZ = -halfSize;

        if (x < minX || x > halfSize || z < minZ || z > halfSize) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpenInventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BlockState state) {
            Block block = state.getBlock();
            if (isVisitor(block.getLocation(), player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUseFlintOrFireCharge(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Material item = player.getInventory().getItemInMainHand().getType();
        if ((item == Material.FLINT_AND_STEEL || item == Material.FIRE_CHARGE) && isVisitor(player.getLocation(), player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFishingRodPull(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY || event.getCaught() == null) {
            return;
        }

        if (isVisitor(event.getCaught().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if ((cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) && isVisitor(event.getTo(), player)) {
            event.setCancelled(true);
        }
    }
}