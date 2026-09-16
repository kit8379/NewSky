package org.me.newsky.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.me.newsky.NewSky;
import org.me.newsky.exceptions.InsufficientFundsException;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Vault provider access and economy operations. Provider calls run on the main thread. */
public class EconomyHandler {
    private final NewSky plugin;

    public EconomyHandler(NewSky plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Double> getBalance(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> economy().getBalance(Bukkit.getOfflinePlayer(playerUuid)), Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public CompletableFuture<Void> withdraw(UUID playerUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            if (!economy().withdrawPlayer(Bukkit.getOfflinePlayer(playerUuid), amount).transactionSuccess()) {
                throw new InsufficientFundsException();
            }
            return null;
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public CompletableFuture<Boolean> deposit(UUID playerUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> economy().depositPlayer(Bukkit.getOfflinePlayer(playerUuid), amount).transactionSuccess(), Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public CompletableFuture<String> format(double amount) {
        return CompletableFuture.supplyAsync(() -> economy().format(amount), Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    private Economy economy() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            throw new IllegalStateException("No Vault economy provider is registered");
        }
        return registration.getProvider();
    }

}
