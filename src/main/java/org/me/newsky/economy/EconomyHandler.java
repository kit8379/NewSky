package org.me.newsky.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.me.newsky.NewSky;
import org.me.newsky.exceptions.InsufficientFundsException;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class EconomyHandler {

    private final NewSky plugin;
    private Economy economy;

    public EconomyHandler(NewSky plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault plugin not found!.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return true;
    }

    public CompletableFuture<Double> getBalance(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
            return economy.getBalance(offlinePlayer);
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public CompletableFuture<Void> withdraw(UUID playerUuid, double amount) {
        return CompletableFuture.runAsync(() -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);

            double balance = economy.getBalance(offlinePlayer);

            if (balance < amount) {
                throw new InsufficientFundsException();
            }

            EconomyResponse response = economy.withdrawPlayer(offlinePlayer, amount);

            if (!response.transactionSuccess()) {
                throw new IllegalStateException("Vault withdraw failed: " + response.errorMessage);
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }
}