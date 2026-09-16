package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.UpgradeDoesNotExistException;
import org.me.newsky.exceptions.UpgradeLevelChangedException;
import org.me.newsky.exceptions.UpgradeLevelDoesNotExistException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /isadmin upgrade <player> <upgradeId> [set <level>]
 */
public class AdminUpgradeCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminUpgradeCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
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
        return config.getAdminUpgradeAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminUpgradePermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminUpgradeSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminUpgradeDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 3 && args.length != 5) {
            return false;
        }

        String ownerName = args[1];
        String upgradeId = args[2];

        if (args.length == 5) {
            if (!args[3].equalsIgnoreCase("set")) {
                return false;
            }

            int level;
            try {
                level = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage(config.getAdminUpgradeInvalidLevelMessage());
                return true;
            }

            api.getPlayerUuid(ownerName).thenCompose(ownerUuidOpt -> {
                if (ownerUuidOpt.isEmpty()) {
                    sender.sendMessage(config.getUnknownPlayerMessage(ownerName));
                    return CompletableFuture.completedFuture(null);
                }

                return api.getIslandUuid(ownerUuidOpt.get()).thenCompose(islandUuid -> api.admin(sender).setUpgradeLevel(islandUuid, upgradeId, level)).thenRun(() -> {
                    sender.sendMessage(config.getAdminUpgradeSetSuccessMessage(upgradeId, level));
                });
            }).exceptionally(ex -> {
                handleFailure(sender, ownerName, upgradeId, ex);
                return null;
            });

            return true;
        }

        api.getPlayerUuid(ownerName).thenCompose(ownerUuidOpt -> {
            if (ownerUuidOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(ownerName));
                return CompletableFuture.completedFuture(null);
            }

            return api.getIslandUuid(ownerUuidOpt.get()).thenCompose(islandUuid -> api.getUpgradeLevel(islandUuid, upgradeId)).thenAccept(level -> {
                sender.sendMessage(config.getAdminUpgradeDetailsHeaderMessage(ownerName, upgradeId));
                sender.sendMessage(config.getAdminUpgradeDetailsCurrentLevelMessage(upgradeId, level));
                sender.sendMessage(config.getAdminUpgradeDetailsCurrentValueMessage(config.getUpgradeLimit(upgradeId, level)));
            });
        }).exceptionally(ex -> {
            handleFailure(sender, ownerName, upgradeId, ex);
            return null;
        });

        return true;
    }

    private void handleFailure(CommandSender sender, String ownerName, String upgradeId, Throwable ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof IslandDoesNotExistException) {
            sender.sendMessage(config.getAdminNoIslandMessage(ownerName));
        } else if (cause instanceof UpgradeDoesNotExistException) {
            sender.sendMessage(config.getPlayerUpgradeInvalidIdMessage(upgradeId));
        } else if (cause instanceof UpgradeLevelDoesNotExistException) {
            sender.sendMessage(config.getAdminUpgradeInvalidLevelMessage());
        } else if (cause instanceof UpgradeLevelChangedException) {
            sender.sendMessage(config.getAdminUpgradeLevelChangedMessage(upgradeId));
        } else {
            sender.sendMessage(config.getUnknownExceptionMessage());
            plugin.severe("Error handling upgrade " + upgradeId + " of island of " + ownerName, ex);
        }
    }

    @Override
    public CompletableFuture<List<String>> tabCompleteAsync(CommandSender sender, String label, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return api.getOnlinePlayersNames().thenApply(names -> names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList())).exceptionally(ex -> Collections.emptyList());
        }

        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return CompletableFuture.completedFuture(config.getUpgradeIds().stream().filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList()));
        }

        if (args.length == 4 && "set".startsWith(args[3].toLowerCase(Locale.ROOT))) {
            return CompletableFuture.completedFuture(List.of("set"));
        }

        if (args.length == 5 && config.isUpgrade(args[2])) {
            String prefix = args[4];
            return CompletableFuture.completedFuture(config.getUpgradeLevels(args[2]).stream().map(String::valueOf).filter(level -> level.startsWith(prefix)).collect(Collectors.toList()));
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}
