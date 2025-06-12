package org.me.newsky.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandProtectionListener implements Listener {

    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final int islandSize;

    public IslandProtectionListener(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.islandSize = config.getIslandSize();
    }

    private boolean canPlayerEdit(Player player, Location location) {
        if (player.isOp()) {
            return true;
        }

        if (location.getWorld() == null || !IslandUtils.isIslandWorld(location.getWorld().getName())) {
            return true;
        }

        UUID islandUuid = IslandUtils.nameToUUID(location.getWorld().getName());

        int centerX = 0;
        int centerZ = 0;
        int half = islandSize / 2;

        int minX = centerX - half;
        int maxX = centerX + half - 1;
        int minZ = centerZ - half;
        int maxZ = centerZ + half - 1;

        int x = location.getBlockX();
        int z = location.getBlockZ();

        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return false;
        }

        UUID playerUuid = player.getUniqueId();
        return cacheHandler.getIslandPlayers(islandUuid).contains(playerUuid) || cacheHandler.isPlayerCooped(islandUuid, playerUuid);
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
        Player player = event.getPlayer();
        Action action = event.getAction();

        if ((action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK) && event.getClickedBlock() != null) {
            if (!canPlayerEdit(player, event.getClickedBlock().getLocation())) {
                event.setCancelled(true);
                player.sendMessage(config.getCannotEditIslandMessage());
                return;
            }
        }

        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Material blockType = event.getClickedBlock().getType();
            ItemStack item = player.getInventory().getItemInMainHand();
            Material itemType = item.getType();

            if (blockType.name().endsWith("PRESSURE_PLATE")) return;

            if (!canPlayerEdit(player, event.getClickedBlock().getLocation())) {
                if (itemType.isEdible()) {
                    return;
                }
                if (blockType == Material.ENDER_CHEST) {
                    return;
                }
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCropTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.FARMLAND) {
            if (!canPlayerEdit(event.getPlayer(), event.getClickedBlock().getLocation())) {
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
        if (!canPlayerEdit(player, entity.getLocation())) {
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

        if (event.getEntity() instanceof Player) {
            return;
        }

        if (event.getEntity() instanceof Monster) {
            return;
        }

        if (!canPlayerEdit(damager, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketUse(PlayerBucketEmptyEvent event) {
        if (!canPlayerEdit(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFluidSpread(BlockFromToEvent event) {
        if (!IslandUtils.isIslandWorld(event.getBlock().getWorld().getName())) {
            return;
        }

        int half = islandSize / 2;
        int min = -half;
        int max = half - 1;

        int x = event.getToBlock().getX();
        int z = event.getToBlock().getZ();

        if (x < min || x > max || z < min || z > max) {
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
            if (!canPlayerEdit(player, block.getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUseFlintOrFireCharge(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Material item = player.getInventory().getItemInMainHand().getType();
        if ((item == Material.FLINT_AND_STEEL || item == Material.FIRE_CHARGE) && !canPlayerEdit(player, player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFishingRodPull(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && event.getCaught() != null) {
            if (!canPlayerEdit(event.getPlayer(), event.getCaught().getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if ((cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) && !canPlayerEdit(player, event.getTo())) {
            event.setCancelled(true);
        }
    }
}
