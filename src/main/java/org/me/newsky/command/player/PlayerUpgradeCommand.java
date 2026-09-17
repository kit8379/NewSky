package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.InsufficientFundsException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.UpgradeDoesNotExistException;
import org.me.newsky.exceptions.UpgradeIslandLevelTooLowException;
import org.me.newsky.exceptions.UpgradeLevelChangedException;
import org.me.newsky.exceptions.UpgradeMaxedException;
import org.me.newsky.model.Upgrade;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /is upgrade <upgradeId> [buy]
 */
public class PlayerUpgradeCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerUpgradeCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "upgrade";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerUpgradeAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerUpgradePermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerUpgradeSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerUpgradeDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        if (args.length < 2 || args.length > 3) {
            return false;
        }

        String upgradeId = args[1];
        UUID playerUuid = player.getUniqueId();

        if (args.length == 3) {
            if (!args[2].equalsIgnoreCase("buy")) {
                return false;
            }

            api.player(playerUuid).buyUpgrade(upgradeId).thenAccept(newLevel -> {
                player.sendMessage(config.getPlayerUpgradeBuySuccessMessage(upgradeId, newLevel - 1, newLevel));
            }).exceptionally(ex -> {
                handleFailure(player, upgradeId, ex);
                return null;
            });

            return true;
        }

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.getUpgradeDetails(islandUuid, playerUuid, upgradeId)).thenAccept(details -> {
            sendDetails(player, upgradeId, details);
        }).exceptionally(ex -> {
            handleFailure(player, upgradeId, ex);
            return null;
        });

        return true;
    }

    private void sendDetails(Player player, String upgradeId, Upgrade details) {
        String unknown = config.getUpgradeUnknownValue();

        player.sendMessage(config.getPlayerUpgradeDetailsHeaderMessage(upgradeId));
        player.sendMessage(config.getPlayerUpgradeDetailsCurrentLevelMessage(details.currentLevel()));
        player.sendMessage(config.getPlayerUpgradeDetailsCurrentValueMessage(details.currentValue()));

        if (details.maxed()) {
            player.sendMessage(config.getPlayerUpgradeDetailsNextLevelMessage(unknown));
            player.sendMessage(config.getPlayerUpgradeDetailsNextValueMessage(unknown));
            player.sendMessage(config.getPlayerUpgradeDetailsRequireIslandLevelMessage(unknown));
            player.sendMessage(config.getPlayerUpgradeDetailsYourIslandLevelMessage(details.islandLevel()));
            player.sendMessage(config.getPlayerUpgradeDetailsPriceMessage(unknown));
            player.sendMessage(config.getPlayerUpgradeDetailsYourBalanceMessage(details.balance()));
            player.sendMessage(config.getPlayerUpgradeMaxedMessage(upgradeId));
            return;
        }

        player.sendMessage(config.getPlayerUpgradeDetailsNextLevelMessage(String.valueOf(details.nextLevel())));
        player.sendMessage(config.getPlayerUpgradeDetailsNextValueMessage(details.nextValue()));
        player.sendMessage(config.getPlayerUpgradeDetailsRequireIslandLevelMessage(String.valueOf(details.nextRequireLevel())));
        player.sendMessage(config.getPlayerUpgradeDetailsYourIslandLevelMessage(details.islandLevel()));
        player.sendMessage(config.getPlayerUpgradeDetailsPriceMessage(details.nextPrice()));
        player.sendMessage(config.getPlayerUpgradeDetailsYourBalanceMessage(details.balance()));
        player.sendMessage(details.available() ? config.getPlayerUpgradeDetailsStatusAvailableMessage() : config.getPlayerUpgradeDetailsStatusLockedMessage());
    }

    private void handleFailure(Player player, String upgradeId, Throwable ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof IslandDoesNotExistException) {
            player.sendMessage(config.getPlayerNoIslandMessage());
        } else if (cause instanceof UpgradeDoesNotExistException) {
            player.sendMessage(config.getPlayerUpgradeInvalidIdMessage(upgradeId));
        } else if (cause instanceof UpgradeMaxedException) {
            player.sendMessage(config.getPlayerUpgradeMaxedMessage(upgradeId));
        } else if (cause instanceof UpgradeIslandLevelTooLowException) {
            player.sendMessage(config.getPlayerUpgradeIslandLevelTooLowMessage(upgradeId));
        } else if (cause instanceof InsufficientFundsException) {
            player.sendMessage(config.getPlayerUpgradeNotEnoughMoneyMessage());
        } else if (cause instanceof UpgradeLevelChangedException) {
            player.sendMessage(config.getPlayerUpgradeLevelChangedMessage(upgradeId));
        } else {
            player.sendMessage(config.getUnknownExceptionMessage());
            plugin.severe("Error handling upgrade " + upgradeId + " for player " + player.getName(), ex);
        }
    }

    @Override
    public CompletableFuture<List<String>> tabCompleteAsync(CommandSender sender, String label, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return CompletableFuture.completedFuture(config.getUpgradeIds().stream().filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList()));
        }

        if (args.length == 3 && "buy".startsWith(args[2].toLowerCase(Locale.ROOT))) {
            return CompletableFuture.completedFuture(List.of("buy"));
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}
