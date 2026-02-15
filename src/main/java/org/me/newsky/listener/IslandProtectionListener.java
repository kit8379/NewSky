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
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.UpgradeHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandProtectionListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;

    public IslandProtectionListener(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
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

    private boolean isInsideIslandBoundary(UUID islandUuid, Location location) {
        int islandSizeLevel = plugin.getApi().getCurrentUpgradeLevel(islandUuid, UpgradeHandler.UPGRADE_ISLAND_SIZE);
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

        if (!isInsideIslandBoundary(islandUuid, location)) {
            return false;
        }

        if (player.isOp()) {
            return true;
        }

        UUID playerUuid = player.getUniqueId();
        return plugin.getApi().getIslandPlayers(islandUuid).contains(playerUuid) || plugin.getApi().isPlayerCooped(islandUuid, playerUuid);
    }

    private boolean isAllowedByBoundary(Location location) {
        UUID islandUuid = getIslandUuidIfIslandWorld(location);
        if (islandUuid == null) {
            return true;
        }
        return isInsideIslandBoundary(islandUuid, location);
    }

    private void deny(Player player) {
        player.sendMessage(config.getCannotEditIslandMessage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        if (!canPlayerEdit(player, loc)) {
            event.setCancelled(true);
            deny(player);
            plugin.debug("IslandProtectionListener", "Player " + player.getName() + " tried to break a block in a protected area: " + loc);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlockPlaced().getLocation();

        if (!canPlayerEdit(player, loc)) {
            event.setCancelled(true);
            deny(player);
            plugin.debug("IslandProtectionListener", "Player " + player.getName() + " tried to place a block in a protected area: " + loc);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
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
            plugin.debug("IslandProtectionListener", "Player " + player.getName() + " tried to interact with a block in a protected area: " + loc);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketUse(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        if (!canPlayerEdit(player, loc)) {
            event.setCancelled(true);
            deny(player);
            plugin.debug("IslandProtectionListener", "Player " + player.getName() + " tried to use a bucket in a protected area: " + loc);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidSpread(BlockFromToEvent event) {
        Location toLoc = event.getToBlock().getLocation();

        if (!isAllowedByBoundary(toLoc)) {
            event.setCancelled(true);
            plugin.debug("IslandProtectionListener", "Fluid spread blocked outside island boundary: " + toLoc);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Location loc = event.getBlock().getLocation();

        if (!isAllowedByBoundary(loc)) {
            event.setCancelled(true);
            plugin.debug("IslandProtectionListener", "Block formation blocked outside island boundary: " + loc);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            Block toBlock = block.getRelative(event.getDirection());
            Location toLoc = toBlock.getLocation();

            if (!isAllowedByBoundary(toLoc)) {
                event.setCancelled(true);
                plugin.debug("IslandProtectionListener", "Piston extension blocked outside island boundary: " + toLoc);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            Block toBlock = block.getRelative(event.getDirection());
            Location toLoc = toBlock.getLocation();

            if (!isAllowedByBoundary(toLoc)) {
                event.setCancelled(true);
                plugin.debug("IslandProtectionListener", "Piston retraction blocked outside island boundary: " + toLoc);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobTrample(EntityChangeBlockEvent event) {
        Location loc = event.getBlock().getLocation();

        if (!isAllowedByBoundary(loc)) {
            event.setCancelled(true);
            plugin.debug("IslandProtectionListener", "Entity change block blocked outside island boundary: " + loc);
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

        // You intentionally allow damaging players and monsters
        if (event.getEntity() instanceof Player || event.getEntity() instanceof Monster) {
            return;
        }

        Location loc = event.getEntity().getLocation();
        if (!canPlayerEdit(damager, loc)) {
            event.setCancelled(true);
            deny(damager);
            plugin.debug("IslandProtectionListener", "Player " + damager.getName() + " tried to damage an entity in a protected area: " + loc);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpenInventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof BlockState state)) {
            return;
        }

        Location loc = state.getBlock().getLocation();
        if (!canPlayerEdit(player, loc)) {
            event.setCancelled(true);
            deny(player);
            plugin.debug("IslandProtectionListener", "Player " + player.getName() + " tried to open an inventory in a protected area: " + loc);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFishingRodPull(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }

        if (event.getCaught() == null) {
            return;
        }

        Player player = event.getPlayer();
        Location loc = event.getCaught().getLocation();

        if (!canPlayerEdit(player, loc)) {
            event.setCancelled(true);
            deny(player);
            plugin.debug("IslandProtectionListener", "Player " + player.getName() + " tried to pull an entity with a fishing rod in a protected area: " + loc);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (!canPlayerEdit(player, to)) {
            event.setCancelled(true);
            deny(player);
            plugin.debug("IslandProtectionListener", "Player " + player.getName() + " tried to teleport to a protected area: " + to);
        }
    }
}
