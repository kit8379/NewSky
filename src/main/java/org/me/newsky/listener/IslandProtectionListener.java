package org.me.newsky.listener;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryHolder;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.UpgradeHandler;
import org.me.newsky.model.Island;
import org.me.newsky.util.IslandUtils;
import snapshot.IslandSnapshot;

import java.util.Map;
import java.util.UUID;

public class IslandProtectionListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final IslandSnapshot islandSnapshot;

    public IslandProtectionListener(NewSky plugin, ConfigHandler config, IslandSnapshot islandSnapshot) {
        this.plugin = plugin;
        this.config = config;
        this.islandSnapshot = islandSnapshot;
    }

    private UUID getIslandUuidIfIslandWorld(Location location) {
        if (location == null) {
            return null;
        }

        World world = location.getWorld();
        if (world == null) {
            return null;
        }

        String worldName = world.getName();
        if (!IslandUtils.isIslandWorld(worldName)) {
            return null;
        }

        return IslandUtils.nameToUUID(worldName);
    }

    private boolean isInsideIslandBoundary(Island island, Location location) {
        if (island == null || location == null) {
            return false;
        }

        Map<String, Integer> upgrades = island.getUpgrades();
        int islandSizeLevel = upgrades.getOrDefault(UpgradeHandler.UPGRADE_ISLAND_SIZE, 1);
        int islandSize = plugin.getApi().getIslandSize(islandSizeLevel);

        int x = location.getBlockX();
        int z = location.getBlockZ();
        int half = islandSize / 2;

        return x >= -half && x <= (half - 1) && z >= -half && z <= (half - 1);
    }

    private boolean canPlayerEdit(Player player, Location location) {
        if (player == null) {
            return true;
        }

        UUID islandUuid = getIslandUuidIfIslandWorld(location);
        if (islandUuid == null) {
            return true;
        }

        Island island = islandSnapshot.get(islandUuid);
        if (island == null) {
            return false;
        }

        if (!isInsideIslandBoundary(island, location)) {
            return false;
        }

        if (player.isOp()) {
            return true;
        }

        UUID playerUuid = player.getUniqueId();
        return island.getOwner().equals(playerUuid)
                || island.getMembers().contains(playerUuid)
                || island.getCoops().contains(playerUuid);
    }

    private boolean isAllowedByBoundary(Location location) {
        UUID islandUuid = getIslandUuidIfIslandWorld(location);
        if (islandUuid == null) {
            return true;
        }

        Island island = islandSnapshot.get(islandUuid);
        if (island == null) {
            return false;
        }

        return isInsideIslandBoundary(island, location);
    }

    private void deny(Player player) {
        player.sendMessage(config.getCannotEditIslandMessage());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        if (!canPlayerEdit(player, loc)) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlockPlaced().getLocation();

        if (!canPlayerEdit(player, loc)) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        Player player = event.getPlayer();
        Location loc = clicked.getLocation();

        if (!canPlayerEdit(player, loc)) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketUse(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        if (!canPlayerEdit(player, loc)) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFluidSpread(BlockFromToEvent event) {
        if (!isAllowedByBoundary(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (!isAllowedByBoundary(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (!isAllowedByBoundary(block.getRelative(event.getDirection()).getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (!isAllowedByBoundary(block.getRelative(event.getDirection()).getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobTrample(EntityChangeBlockEvent event) {
        if (!isAllowedByBoundary(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpenInventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof BlockState state)) {
            return;
        }

        if (!canPlayerEdit(player, state.getBlock().getLocation())) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFishingRodPull(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY || event.getCaught() == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!canPlayerEdit(player, event.getCaught().getLocation())) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (!canPlayerEdit(player, event.getTo())) {
            event.setCancelled(true);
            deny(player);
        }
    }
}