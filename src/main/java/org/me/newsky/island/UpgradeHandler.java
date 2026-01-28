// UpgradeHandler.java
package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.UpgradeDoesNotExistException;
import org.me.newsky.exceptions.UpgradeIslandLevelTooLowException;
import org.me.newsky.exceptions.UpgradeMaxedException;
import org.me.newsky.model.UpgradeResult;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class UpgradeHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final Cache cache;

    public static final String UPGRADE_TEAM_LIMIT = "team-limit";
    public static final String UPGRADE_WARPS_LIMIT = "warps-limit";
    public static final String UPGRADE_COOP_LIMIT = "coop-limit";
    public static final String UPGRADE_ISLAND_SIZE = "island-size";
    public static final String UPGRADE_GENERATOR_RATES = "generator-rates";

    public UpgradeHandler(NewSky plugin, ConfigHandler config, Cache cache) {
        this.plugin = plugin;
        this.config = config;
        this.cache = cache;
    }

    // ================================================================================================================
    // Upgrade IDs
    // ================================================================================================================

    public Set<String> getUpgradeIds() {
        Set<String> ids = config.getUpgradeIds();
        if (ids == null) {
            return Collections.emptySet();
        }
        return ids;
    }

    public boolean hasUpgrade(String upgradeId) {
        return getUpgradeIds().contains(upgradeId);
    }

    // ================================================================================================================
    // Levels
    // ================================================================================================================


    public Set<Integer> getLevels(String upgradeId) {
        Set<Integer> levels = config.getUpgradeLevels(upgradeId);
        if (levels == null) {
            return Collections.emptySet();
        }
        return levels;
    }

    public int getNextLevel(String upgradeId, int currentLevel) {
        int next = Integer.MAX_VALUE;
        for (int lvl : getLevels(upgradeId)) {
            if (lvl > currentLevel && lvl < next) {
                next = lvl;
            }
        }
        if (next == Integer.MAX_VALUE) {
            return -1;
        }
        return next;
    }

    // ================================================================================================================
    // Value getters (fallback to level 1 when missing)
    // ================================================================================================================

    public int getTeamLimit(int level) {
        int v = config.getUpgradeTeamLimit(level);
        if (v > 0) {
            return v;
        }
        return config.getUpgradeTeamLimit(1);
    }

    public int getWarpsLimit(int level) {
        int v = config.getUpgradeWarpsLimit(level);
        if (v > 0) {
            return v;
        }
        return config.getUpgradeWarpsLimit(1);
    }

    public int getCoopLimit(int level) {
        int v = config.getUpgradeCoopLimit(level);
        if (v > 0) {
            return v;
        }
        return config.getUpgradeCoopLimit(1);
    }

    public int getIslandSize(int level) {
        int v = config.getUpgradeIslandSize(level);
        if (v > 0) {
            return v;
        }
        return config.getUpgradeIslandSize(1);
    }

    public Map<String, Integer> getGeneratorRates(int level) {
        Map<String, Integer> raw = config.getUpgradeGeneratorRates(level);
        if (raw == null || raw.isEmpty()) {
            return config.getUpgradeGeneratorRates(1);
        }
        return raw;
    }

    // ================================================================================================================
    // Upgrade operations
    // ================================================================================================================

    public CompletableFuture<UpgradeResult> upgradeToNextLevel(UUID islandUuid, String upgradeId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!hasUpgrade(upgradeId)) {
                throw new UpgradeDoesNotExistException();
            }

            int islandLevel = cache.getIslandLevel(islandUuid);
            int oldLevel = cache.getIslandUpgradeLevel(islandUuid, upgradeId);
            if (oldLevel <= 0) {
                oldLevel = 1;
            }

            int nextLevel = getNextLevel(upgradeId, oldLevel);
            if (nextLevel == -1) {
                throw new UpgradeMaxedException();
            }

            int requireIslandLevel = config.getUpgradeRequireIslandLevel(upgradeId, nextLevel);
            if (islandLevel < requireIslandLevel) {
                throw new UpgradeIslandLevelTooLowException();
            }

            cache.updateIslandUpgradeLevel(islandUuid, upgradeId, nextLevel);

            return new UpgradeResult(upgradeId, oldLevel, nextLevel, requireIslandLevel);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> setUpgradeLevel(UUID islandUuid, String upgradeId, int level) {
        return CompletableFuture.runAsync(() -> {
            if (!hasUpgrade(upgradeId)) {
                throw new UpgradeDoesNotExistException();
            }
            cache.updateIslandUpgradeLevel(islandUuid, upgradeId, Math.max(level, 1));
        }, plugin.getBukkitAsyncExecutor());
    }
}