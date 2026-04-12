package org.me.newsky.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.LimitHandler;
import org.me.newsky.util.IslandUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class IslandLimitListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final LimitHandler limitHandler;

    public IslandLimitListener(NewSky plugin, ConfigHandler config, LimitHandler limitHandler) {
        this.plugin = plugin;
        this.config = config;
        this.limitHandler = limitHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlockPlaced().getWorld());
        if (islandUuid == null) {
            return;
        }

        Material oldType = event.getBlockReplacedState().getType();
        Material newType = event.getBlockPlaced().getType();

        if (!applyChangeWithLimit(event, islandUuid, oldType, newType, event.getPlayer())) {
            plugin.debug("IslandLimitListener", "Denied block place due to limit: island=" + islandUuid + ", old=" + oldType + ", new=" + newType + ", player=" + event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block targetBlock = event.getBlockClicked().getRelative(event.getBlockFace());
        UUID islandUuid = getIslandUuidIfIslandWorld(targetBlock.getWorld());
        if (islandUuid == null) {
            return;
        }

        Material oldType = targetBlock.getType();
        Material newType = bucketToPlacedMaterial(event.getBucket());

        if (newType == null) {
            return;
        }

        if (!applyChangeWithLimit(event, islandUuid, oldType, newType, event.getPlayer())) {
            plugin.debug("IslandLimitListener", "Denied bucket empty due to limit: island=" + islandUuid + ", old=" + oldType + ", new=" + newType + ", player=" + event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlock().getWorld());
        if (islandUuid == null) {
            return;
        }

        Material oldType = event.getBlock().getType();
        Material newType = event.getNewState().getType();

        if (!applyChangeWithLimit(event, islandUuid, oldType, newType, null)) {
            plugin.debug("IslandLimitListener", "Denied block form due to limit: island=" + islandUuid + ", old=" + oldType + ", new=" + newType + ", location=" + event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlock().getWorld());
        if (islandUuid == null) {
            return;
        }

        Material oldType = event.getBlock().getType();
        Material newType = event.getNewState().getType();

        if (!applyChangeWithLimit(event, islandUuid, oldType, newType, null)) {
            plugin.debug("IslandLimitListener", "Denied block spread due to limit: island=" + islandUuid + ", old=" + oldType + ", new=" + newType + ", location=" + event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlock().getWorld());
        if (islandUuid == null) {
            return;
        }

        Material oldType = event.getBlock().getType();
        Material newType = event.getNewState().getType();

        if (!applyChangeWithLimit(event, islandUuid, oldType, newType, null)) {
            plugin.debug("IslandLimitListener", "Denied block grow due to limit: island=" + islandUuid + ", old=" + oldType + ", new=" + newType + ", location=" + event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoistureChange(MoistureChangeEvent event) {
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlock().getWorld());
        if (islandUuid == null) {
            return;
        }

        Material oldType = event.getBlock().getType();
        Material newType = event.getNewState().getType();

        if (!applyChangeWithLimit(event, islandUuid, oldType, newType, null)) {
            plugin.debug("IslandLimitListener", "Denied moisture change due to limit: island=" + islandUuid + ", old=" + oldType + ", new=" + newType + ", location=" + event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidSpread(BlockFromToEvent event) {
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlock().getWorld());
        if (islandUuid == null) {
            return;
        }

        Material movingType = event.getBlock().getType();
        if (!isTracked(movingType)) {
            return;
        }

        Block toBlock = event.getToBlock();
        Material oldType = toBlock.getType();

        if (!applyChangeWithLimit(event, islandUuid, oldType, movingType, null)) {
            plugin.debug("IslandLimitListener", "Denied fluid spread due to limit: island=" + islandUuid + ", old=" + oldType + ", new=" + movingType + ", from=" + event.getBlock().getLocation() + ", to=" + toBlock.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlock().getWorld());
        if (islandUuid == null) {
            return;
        }

        Player player = event.getEntity() instanceof Player p ? p : null;
        Material oldType = event.getBlock().getType();
        Material newType = event.getTo();

        if (!applyChangeWithLimit(event, islandUuid, oldType, newType, player)) {
            plugin.debug("IslandLimitListener", "Denied entity block change due to limit: island=" + islandUuid + ", old=" + oldType + ", new=" + newType + ", entity=" + event.getEntity().getType() + ", location=" + event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        Location location = event.getLocation();

        UUID islandUuid = getIslandUuidIfIslandWorld(location.getWorld());
        if (islandUuid == null) {
            return;
        }

        ChangeSet changeSet = collectBlockStateChanges(event.getBlocks());
        if (changeSet.isEmpty()) {
            return;
        }

        if (!canApplyAdditions(islandUuid, changeSet.additions())) {
            event.setCancelled(true);
            denyLimit(event.getPlayer(), firstTrackedMaterial(changeSet.additions()));
            plugin.debug("IslandLimitListener", "Denied structure grow due to limit: island=" + islandUuid + ", additions=" + changeSet.additions() + ", removals=" + changeSet.removals());
            return;
        }

        applyAdditions(islandUuid, changeSet.additions());
        applyRemovals(islandUuid, changeSet.removals());

        plugin.debug("IslandLimitListener", "Applied structure grow limit changes: island=" + islandUuid + ", additions=" + changeSet.additions() + ", removals=" + changeSet.removals());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlock().getWorld());
        if (islandUuid == null) {
            return;
        }

        ChangeSet changeSet = collectBlockStateChanges(event.getBlocks());
        if (changeSet.isEmpty()) {
            return;
        }

        if (!canApplyAdditions(islandUuid, changeSet.additions())) {
            event.setCancelled(true);
            denyLimit(event.getPlayer(), firstTrackedMaterial(changeSet.additions()));
            plugin.debug("IslandLimitListener", "Denied block fertilize due to limit: island=" + islandUuid + ", additions=" + changeSet.additions() + ", removals=" + changeSet.removals());
            return;
        }

        applyAdditions(islandUuid, changeSet.additions());
        applyRemovals(islandUuid, changeSet.removals());

        plugin.debug("IslandLimitListener", "Applied block fertilize limit changes: island=" + islandUuid + ", additions=" + changeSet.additions() + ", removals=" + changeSet.removals());
    }

    // =====================================================================================
    // Removal / destruction events
    // =====================================================================================
    // These should run after success only.
    // =====================================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlock().getWorld());
        if (islandUuid == null) {
            return;
        }

        decrementIfTracked(islandUuid, event.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block targetBlock = event.getBlockClicked().getRelative(event.getBlockFace());
        UUID islandUuid = getIslandUuidIfIslandWorld(targetBlock.getWorld());
        if (islandUuid == null) {
            return;
        }

        decrementIfTracked(islandUuid, targetBlock.getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlock().getWorld());
        if (islandUuid == null) {
            return;
        }

        decrementIfTracked(islandUuid, event.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlock().getWorld());
        if (islandUuid == null) {
            return;
        }

        Material oldType = event.getBlock().getType();
        Material newType = event.getNewState().getType();

        applyRemovalOrReplacementAfterSuccess(islandUuid, oldType, newType);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlock().getWorld());
        if (islandUuid == null) {
            return;
        }

        decrementIfTracked(islandUuid, event.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlock().getWorld());
        if (islandUuid == null) {
            return;
        }

        for (Block block : event.blockList()) {
            decrementIfTracked(islandUuid, block.getType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Location location = event.getLocation();

        UUID islandUuid = getIslandUuidIfIslandWorld(location.getWorld());
        if (islandUuid == null) {
            return;
        }

        for (Block block : event.blockList()) {
            decrementIfTracked(islandUuid, block.getType());
        }
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private boolean applyChangeWithLimit(Cancellable event, UUID islandUuid, Material oldType, Material newType, Player player) {
        if (oldType == newType) {
            return true;
        }

        boolean oldTracked = isTracked(oldType);
        boolean newTracked = isTracked(newType);

        if (!oldTracked && !newTracked) {
            return true;
        }

        if (oldTracked) {
            limitHandler.decrement(islandUuid, oldType);
        }

        if (newTracked && !limitHandler.tryIncrement(islandUuid, newType)) {
            if (oldTracked) {
                restoreIfTracked(islandUuid, oldType);
            }

            event.setCancelled(true);
            denyLimit(player, newType);
            return false;
        }

        return true;
    }

    private void applyRemovalOrReplacementAfterSuccess(UUID islandUuid, Material oldType, Material newType) {
        if (oldType == newType) {
            return;
        }

        boolean oldTracked = isTracked(oldType);
        boolean newTracked = isTracked(newType);

        if (oldTracked) {
            limitHandler.decrement(islandUuid, oldType);
        }

        if (newTracked) {
            restoreIfTracked(islandUuid, newType);
        }
    }

    private ChangeSet collectBlockStateChanges(List<BlockState> states) {
        EnumMap<Material, Integer> additions = new EnumMap<>(Material.class);
        EnumMap<Material, Integer> removals = new EnumMap<>(Material.class);

        for (BlockState state : states) {
            if (state == null) {
                continue;
            }

            Material oldType = state.getBlock().getType();
            Material newType = state.getType();

            if (oldType == newType) {
                continue;
            }

            if (isTracked(oldType)) {
                removals.merge(oldType, 1, Integer::sum);
            }

            if (isTracked(newType)) {
                additions.merge(newType, 1, Integer::sum);
            }
        }

        return new ChangeSet(additions, removals);
    }

    private boolean canApplyAdditions(UUID islandUuid, Map<Material, Integer> additions) {
        if (additions.isEmpty()) {
            return true;
        }

        for (Map.Entry<Material, Integer> entry : additions.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();

            for (int i = 0; i < amount; i++) {
                if (!limitHandler.tryIncrement(islandUuid, material)) {
                    rollbackPreviewAdditions(islandUuid, additions, material, i);
                    return false;
                }
            }
        }

        rollbackPreviewAllAdditions(islandUuid, additions);
        return true;
    }

    private void applyAdditions(UUID islandUuid, Map<Material, Integer> additions) {
        for (Map.Entry<Material, Integer> entry : additions.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();

            for (int i = 0; i < amount; i++) {
                restoreIfTracked(islandUuid, material);
            }
        }
    }

    private void applyRemovals(UUID islandUuid, Map<Material, Integer> removals) {
        for (Map.Entry<Material, Integer> entry : removals.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();

            for (int i = 0; i < amount; i++) {
                limitHandler.decrement(islandUuid, material);
            }
        }
    }

    private void rollbackPreviewAdditions(UUID islandUuid, Map<Material, Integer> additions, Material failedMaterial, int successfulCountForFailedMaterial) {
        for (Map.Entry<Material, Integer> entry : additions.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();

            int rollbackAmount;
            if (material == failedMaterial) {
                rollbackAmount = successfulCountForFailedMaterial;
            } else {
                rollbackAmount = amount;
            }

            for (int i = 0; i < rollbackAmount; i++) {
                limitHandler.decrement(islandUuid, material);
            }

            if (material == failedMaterial) {
                break;
            }
        }
    }

    private void rollbackPreviewAllAdditions(UUID islandUuid, Map<Material, Integer> additions) {
        for (Map.Entry<Material, Integer> entry : additions.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();

            for (int i = 0; i < amount; i++) {
                limitHandler.decrement(islandUuid, material);
            }
        }
    }

    private void decrementIfTracked(UUID islandUuid, Material type) {
        if (isTracked(type)) {
            limitHandler.decrement(islandUuid, type);
        }
    }

    private void restoreIfTracked(UUID islandUuid, Material type) {
        if (!isTracked(type)) {
            return;
        }

        if (!limitHandler.tryIncrement(islandUuid, type)) {
            plugin.debug("IslandLimitListener", "Failed to restore tracked block count during internal correction: island=" + islandUuid + ", type=" + type);
        }
    }

    private boolean isTracked(Material type) {
        return type != null && config.getBlockLimit(type.name()) > 0;
    }

    private Material firstTrackedMaterial(Map<Material, Integer> additions) {
        for (Material material : additions.keySet()) {
            return material;
        }
        return null;
    }

    private void denyLimit(Player player, Material material) {
        if (player == null) {
            return;
        }

        String materialName = material == null ? "BLOCK" : material.name();
        int limit = material == null ? 0 : config.getBlockLimit(material.name());

        player.sendRichMessage("<red>You have reached the island limit for <yellow>" + materialName + "</yellow><red>. Limit: <yellow>" + limit + "</yellow>");
    }

    private UUID getIslandUuidIfIslandWorld(World world) {
        if (world == null) {
            return null;
        }

        String worldName = world.getName();
        if (!IslandUtils.isIslandWorld(worldName)) {
            return null;
        }

        return IslandUtils.nameToUUID(worldName);
    }

    private Material bucketToPlacedMaterial(Material bucket) {
        if (bucket == null) {
            return null;
        }

        return switch (bucket) {
            case WATER_BUCKET -> Material.WATER;
            case LAVA_BUCKET -> Material.LAVA;
            default -> null;
        };
    }

    private record ChangeSet(EnumMap<Material, Integer> additions, EnumMap<Material, Integer> removals) {
        private boolean isEmpty() {
            return additions.isEmpty() && removals.isEmpty();
        }
    }
}