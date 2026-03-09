package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.CannotCoopIslandPlayerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerAlreadyCoopedException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /isadmin coop <owner> <player>
 */
public class AdminCoopCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminCoopCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "coop";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminCoopAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminCoopPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminCoopSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminCoopDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String ownerName = args[1];
        String targetName = args[2];

        api.getPlayerUuid(ownerName).thenCompose(ownerUuidOpt -> {
            if (ownerUuidOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(ownerName));
                return CompletableFuture.completedFuture(null);
            }

            return api.getPlayerUuid(targetName).thenCompose(targetUuidOpt -> {
                if (targetUuidOpt.isEmpty()) {
                    sender.sendMessage(config.getUnknownPlayerMessage(targetName));
                    return CompletableFuture.completedFuture(null);
                }

                return api.getIslandUuid(ownerUuidOpt.get()).thenCompose(islandUuid -> {
                    return api.addCoop(islandUuid, targetUuidOpt.get());
                }).thenRun(() -> {
                    sender.sendMessage(config.getAdminCoopSuccessMessage(ownerName, targetName));
                    api.sendPlayerMessage(targetUuidOpt.get(), config.getWasCoopedToIslandMessage(ownerName));
                });
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(ownerName));
            } else if (cause instanceof PlayerAlreadyCoopedException) {
                sender.sendMessage(config.getPlayerAlreadyCoopedMessage(targetName));
            } else if (cause instanceof CannotCoopIslandPlayerException) {
                sender.sendMessage(config.getPlayerCannotCoopIslandPlayerMessage());
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error cooping player " + targetName + " to island of " + ownerName, ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public CompletableFuture<List<String>> tabCompleteAsync(CommandSender sender, String label, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return api.getOnlinePlayersNames().thenApply(names -> names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList())).exceptionally(ex -> Collections.emptyList());
        }

        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return api.getOnlinePlayersNames().thenApply(names -> names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList())).exceptionally(ex -> Collections.emptyList());
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}