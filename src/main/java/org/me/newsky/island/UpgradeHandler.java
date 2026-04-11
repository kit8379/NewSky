package org.me.newsky.island;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.me.newsky.NewSky;
import org.me.newsky.cache.DataCache;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.lock.IslandUpgradeLock;
import org.me.newsky.model.Upgrade;
import org.me.newsky.network.IslandDistributor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class UpgradeHandler {

    public static final String UPGRADE_TEAM_LIMIT = "team-limit";
    public static final String UPGRADE_WARP_LIMIT = "warp-limit";
    public static final String UPGRADE_HOME_LIMIT = "home-limit";
    public static final String UPGRADE_COOP_LIMIT = "coop-limit";
    public static final String UPGRADE_ISLAND_SIZE = "island-size";
    public static final String UPGRADE_GENERATOR_RATES = "generator-rates";
    public static final String UPGRADE_BIOMES = "biomes";

    private final NewSky plugin;
    private final ConfigHandler config;
    private final DataCache dataCache;
    private final IslandDistributor islandDistributor;
    private final IslandUpgradeLock islandUpgradeLock;

    public UpgradeHandler(NewSky plugin, ConfigHandler config, DataCache dataCache, IslandDistributor islandDistributor, IslandUpgradeLock islandUpgradeLock) {
        this.plugin = plugin;
        this.config = config;
        this.dataCache = dataCache;
        this.islandDistributor = islandDistributor;
        this.islandUpgradeLock = islandUpgradeLock;
    }

    // ================================================================================================================
    // Upgrade
    // ================================================================================================================

    public CompletableFuture<Upgrade> upgradeToNextLevel(UUID islandUuid, UUID playerUuid, String upgradeId) {
        return CompletableFuture.runAsync(() -> {
        }, plugin.getBukkitAsyncExecutor()).thenCompose(ignored -> islandUpgradeLock.withLock(islandUuid, () -> CompletableFuture.supplyAsync(() -> {
            if (!getUpgradeIds().contains(upgradeId)) {
                throw new UpgradeDoesNotExistException();
            }

            int islandLevel = dataCache.getIslandLevel(islandUuid);
            int oldLevel = dataCache.getIslandUpgradeLevel(islandUuid, upgradeId);

            int nextLevel = getNextUpgradeLevel(upgradeId, oldLevel);
            if (nextLevel == -1) {
                throw new UpgradeMaxedException();
            }

            int requireIslandLevel = config.getUpgradeRequireIslandLevel(upgradeId, nextLevel);
            if (islandLevel < requireIslandLevel) {
                throw new UpgradeIslandLevelTooLowException();
            }

            double price = getUpgradePrice(upgradeId, nextLevel);

            return new Upgrade(upgradeId, oldLevel, nextLevel, requireIslandLevel, price);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(result -> {
            if (result.getPrice() <= 0D) {
                return CompletableFuture.completedFuture(result);
            }

            return CompletableFuture.supplyAsync(() -> {
                withdraw(playerUuid, result.getPrice());
                return result;
            }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
        }).thenApplyAsync(result -> {
            dataCache.updateIslandUpgradeLevel(islandUuid, upgradeId, result.getNewLevel());

            if (UPGRADE_ISLAND_SIZE.equals(upgradeId)) {
                islandDistributor.updateBorder(islandUuid, getIslandSize(result.getNewLevel()));
            }

            islandDistributor.reloadSnapshot(islandUuid);

            return result;
        }, plugin.getBukkitAsyncExecutor())));
    }

    public CompletableFuture<Void> setUpgradeLevel(UUID islandUuid, String upgradeId, int level) {
        return CompletableFuture.runAsync(() -> {
        }, plugin.getBukkitAsyncExecutor()).thenCompose(ignored -> islandUpgradeLock.withLock(islandUuid, () -> CompletableFuture.runAsync(() -> {
            if (!getUpgradeIds().contains(upgradeId)) {
                throw new UpgradeDoesNotExistException();
            }

            Set<Integer> levels = getUpgradeLevels(upgradeId);
            if (!levels.contains(level)) {
                throw new UpgradeLevelDoesNotExistException();
            }

            dataCache.updateIslandUpgradeLevel(islandUuid, upgradeId, level);

            if (UPGRADE_ISLAND_SIZE.equals(upgradeId)) {
                islandDistributor.updateBorder(islandUuid, getIslandSize(level));
            }

            islandDistributor.reloadSnapshot(islandUuid);
        }, plugin.getBukkitAsyncExecutor())));
    }

    public CompletableFuture<Integer> getCurrentUpgradeLevel(UUID islandUuid, String upgradeId) {
        return CompletableFuture.supplyAsync(() -> dataCache.getIslandUpgradeLevel(islandUuid, upgradeId), plugin.getBukkitAsyncExecutor());
    }

    // ================================================================================================================
    // Internal Economy
    // ================================================================================================================

    private void withdraw(UUID playerUuid, double amount) {
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            throw new IllegalStateException("Vault economy is not available.");
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);

        double balance = economy.getBalance(offlinePlayer);
        if (balance < amount) {
            throw new InsufficientFundsException();
        }

        EconomyResponse response = economy.withdrawPlayer(offlinePlayer, amount);
        if (!response.transactionSuccess()) {
            throw new IllegalStateException("Vault withdraw failed: " + response.errorMessage);
        }
    }

    // ================================================================================================================
    // Getters
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

        return next == Integer.MAX_VALUE ? -1 : next;
    }

    public int getUpgradeRequireIslandLevel(String upgradeId, int level) {
        return config.getUpgradeRequireIslandLevel(upgradeId, level);
    }

    public double getUpgradePrice(String upgradeId, int level) {
        return config.getUpgradePrice(upgradeId, level);
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

    public Set<String> getBiomeAllowList(int level) {
        return config.getUpgradeBiomes(level);
    }
}