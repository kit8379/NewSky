package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.economy.EconomyHandler;
import org.me.newsky.exceptions.UpgradeDoesNotExistException;
import org.me.newsky.exceptions.UpgradeIslandLevelTooLowException;
import org.me.newsky.exceptions.UpgradeLevelDoesNotExistException;
import org.me.newsky.exceptions.UpgradeMaxedException;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Upgrade;
import org.me.newsky.network.IslandDistributor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Island upgrades: one level per island for each upgrade id in the config, bought with Vault
 * money and gated by island level. Limits are enforced inside database write transactions;
 * island size is applied by the host to its snapshot and world border.
 */
public class UpgradeHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final DatabaseHandler database;
    private final IslandDistributor islandDistributor;
    private final EconomyHandler economy;

    public UpgradeHandler(NewSky plugin, ConfigHandler config, DatabaseHandler database, IslandDistributor islandDistributor, EconomyHandler economy) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.islandDistributor = islandDistributor;
        this.economy = economy;
    }

    /**
     * Withdraws first, then compare-and-sets the level; any failure after the withdrawal
     * refunds it. Completes with the level bought.
     */
    public CompletableFuture<Integer> buyUpgrade(Actor actor, UUID islandUuid, UUID payerUuid, String upgradeId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!config.isUpgrade(upgradeId)) {
                throw new UpgradeDoesNotExistException();
            }

            int current = database.getIslandUpgradeLevel(islandUuid, upgradeId);
            if (current >= config.getUpgradeLevels(upgradeId).getLast()) {
                throw new UpgradeMaxedException();
            }

            int next = current + 1;
            if (database.getIslandLevel(islandUuid) < config.getUpgradeRequireLevel(upgradeId, next)) {
                throw new UpgradeIslandLevelTooLowException();
            }

            return new Purchase(current, next, config.getUpgradePrice(upgradeId, next));
        }, plugin.getBukkitAsyncExecutor()).thenCompose(purchase -> economy.withdraw(payerUuid, purchase.price()).thenApply(v -> purchase)).thenComposeAsync(purchase -> islandDistributor.setUpgradeLevel(actor, islandUuid, upgradeId, purchase.current(), purchase.next()).thenApply(v -> purchase.next()).exceptionallyCompose(error -> refund(payerUuid, purchase.price(), upgradeId).thenCompose(v -> CompletableFuture.failedFuture(error))), plugin.getBukkitAsyncExecutor());
    }

    /**
     * Operator set: no island level or price check. Still compare-and-set against the level
     * read here, so a purchase landing in between is reported instead of overwritten.
     */
    public CompletableFuture<Void> setUpgradeLevel(Actor actor, UUID islandUuid, String upgradeId, int level) {
        return CompletableFuture.supplyAsync(() -> {
            if (!config.isUpgrade(upgradeId)) {
                throw new UpgradeDoesNotExistException();
            }

            if (!config.getUpgradeLevels(upgradeId).contains(level)) {
                throw new UpgradeLevelDoesNotExistException();
            }

            return database.getIslandUpgradeLevel(islandUuid, upgradeId);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(current -> islandDistributor.setUpgradeLevel(actor, islandUuid, upgradeId, current, level));
    }

    public CompletableFuture<Integer> getUpgradeLevel(UUID islandUuid, String upgradeId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!config.isUpgrade(upgradeId)) {
                throw new UpgradeDoesNotExistException();
            }
            return database.getIslandUpgradeLevel(islandUuid, upgradeId);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Upgrade> getUpgradeDetails(UUID islandUuid, UUID playerUuid, String upgradeId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!config.isUpgrade(upgradeId)) {
                throw new UpgradeDoesNotExistException();
            }
            return new Progress(database.getIslandUpgradeLevel(islandUuid, upgradeId), database.getIslandLevel(islandUuid));
        }, plugin.getBukkitAsyncExecutor()).thenCompose(progress -> economy.getBalance(playerUuid).thenCompose(balance -> {
            int current = progress.currentLevel();
            int currentLimit = config.getUpgradeLimit(upgradeId, current);
            if (current >= config.getUpgradeLevels(upgradeId).getLast()) {
                return economy.format(balance).thenApply(formattedBalance -> new Upgrade(current, currentLimit, true, 0, 0, 0, null, false, progress.islandLevel(), formattedBalance));
            }

            int next = current + 1;
            int requireLevel = config.getUpgradeRequireLevel(upgradeId, next);
            double price = config.getUpgradePrice(upgradeId, next);
            boolean available = progress.islandLevel() >= requireLevel && balance >= price;
            return economy.format(price).thenCombine(economy.format(balance), (formattedPrice, formattedBalance) -> new Upgrade(current, currentLimit, false, next, config.getUpgradeLimit(upgradeId, next), requireLevel, formattedPrice, available, progress.islandLevel(), formattedBalance));
        }));
    }

    private CompletableFuture<Void> refund(UUID payerUuid, double amount, String upgradeId) {
        return economy.deposit(payerUuid, amount).thenAccept(success -> {
            if (!success) {
                plugin.severe("Could not refund " + amount + " to " + payerUuid + " after a failed " + upgradeId + " upgrade purchase.");
            }
        });
    }

    private record Purchase(int current, int next, double price) {
    }

    private record Progress(int currentLevel, int islandLevel) {
    }
}
