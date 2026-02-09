// UpgradeHandler.java
package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.UpgradeDoesNotExistException;
import org.me.newsky.exceptions.UpgradeIslandLevelTooLowException;
import org.me.newsky.exceptions.UpgradeLevelDoesNotExistException;
import org.me.newsky.exceptions.UpgradeMaxedException;
import org.me.newsky.model.UpgradeResult;
import org.me.newsky.network.distributor.IslandDistributor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class UpgradeHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final Cache cache;
    private final IslandDistributor islandDistributor;

    public static final String UPGRADE_TEAM_LIMIT = "team-limit";
    public static final String UPGRADE_WARP_LIMIT = "warp-limit";
    public static final String UPGRADE_HOME_LIMIT = "home-limit";
    public static final String UPGRADE_COOP_LIMIT = "coop-limit";
    public static final String UPGRADE_ISLAND_SIZE = "island-size";
    public static final String UPGRADE_GENERATOR_RATES = "generator-rates";

    public UpgradeHandler(NewSky plugin, ConfigHandler config, Cache cache, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.config = config;
        this.cache = cache;
        this.islandDistributor = islandDistributor;
    }

    // ================================================================================================================
    // Config Value Getters
    // ================================================================================================================

    public Set<String> getUpgradeIds() {
        return config.getUpgradeIds();
    }

    public Set<Integer> getUpgradeLevels(String upgradeId) {
        return config.getUpgradeLevels(upgradeId);
    }

    public int getNextUpgradeLevel(String upgradeId, int currentLevel) {
        int next = Integer.MAX_VALUE;

        for (int lvl : getUpgradeLevels(upgradeId)) {
            if (lvl > currentLevel && lvl < next) {
                next = lvl;
            }
        }

        return (next == Integer.MAX_VALUE) ? -1 : next;
    }

    public int getUpgradeRequireIslandLevel(String upgradeId, int level) {
        return config.getUpgradeRequireIslandLevel(upgradeId, level);
    }

    public int getTeamLimit(int level) {
        return config.getUpgradeTeamLimit(level);
    }

    public int getWarpLimit(int level) {
        return config.getUpgradeWarpLimit(level);
    }

    public int getHomeLimit(int level) {
        return config.getUpgradeHomeLimit(level);
    }

    public int getCoopLimit(int level) {
        return config.getUpgradeCoopLimit(level);
    }

    public int getIslandSize(int level) {
        return config.getUpgradeIslandSize(level);
    }

    public Map<String, Double> getGeneratorRates(int level) {
        return config.getUpgradeGeneratorRates(level);
    }

    // ================================================================================================================
    // Upgrade operations
    // ================================================================================================================

    public CompletableFuture<UpgradeResult> upgradeToNextLevel(UUID islandUuid, String upgradeId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!getUpgradeIds().contains(upgradeId)) {
                throw new UpgradeDoesNotExistException();
            }

            int islandLevel = cache.getIslandLevel(islandUuid);
            int oldLevel = cache.getIslandUpgradeLevel(islandUuid, upgradeId);

            int nextLevel = getNextUpgradeLevel(upgradeId, oldLevel);
            if (nextLevel == -1) {
                throw new UpgradeMaxedException();
            }

            int requireIslandLevel = config.getUpgradeRequireIslandLevel(upgradeId, nextLevel);
            if (islandLevel < requireIslandLevel) {
                throw new UpgradeIslandLevelTooLowException();
            }

            cache.updateIslandUpgradeLevel(islandUuid, upgradeId, nextLevel);

            if (upgradeId.equals(UPGRADE_ISLAND_SIZE)) {
                islandDistributor.updateIslandBorder(islandUuid, getIslandSize(nextLevel));
            }

            return new UpgradeResult(upgradeId, oldLevel, nextLevel, requireIslandLevel);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> setUpgradeLevel(UUID islandUuid, String upgradeId, int level) {
        return CompletableFuture.runAsync(() -> {
            if (!getUpgradeIds().contains(upgradeId)) {
                throw new UpgradeDoesNotExistException();
            }

            Set<Integer> levels = getUpgradeLevels(upgradeId);
            if (!levels.contains(level)) {
                throw new UpgradeLevelDoesNotExistException();
            }

            cache.updateIslandUpgradeLevel(islandUuid, upgradeId, level);

            if (upgradeId.equals(UPGRADE_ISLAND_SIZE)) {
                islandDistributor.updateIslandBorder(islandUuid, getIslandSize(level));
            }
        }, plugin.getBukkitAsyncExecutor());
    }

    public int getCurrentUpgradeLevel(UUID islandUuid, String upgradeId) {
        return cache.getIslandUpgradeLevel(islandUuid, upgradeId);
    }
}
