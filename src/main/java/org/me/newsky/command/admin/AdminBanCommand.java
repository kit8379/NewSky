package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.CannotBanIslandPlayerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerAlreadyBannedException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /isadmin ban <owner> <player>
 */
public class AdminBanCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminBanCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "ban";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminBanAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminBanPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminBanSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminBanDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String islandOwnerName = args[1];
        String banPlayerName = args[2];

        api.getPlayerUuid(islandOwnerName).thenCompose(islandOwnerUuidOpt -> {
            if (islandOwnerUuidOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(islandOwnerName));
                return CompletableFuture.completedFuture(null);
            }

            return api.getPlayerUuid(banPlayerName).thenCompose(targetPlayerUuidOpt -> {
                if (targetPlayerUuidOpt.isEmpty()) {
                    sender.sendMessage(config.getUnknownPlayerMessage(banPlayerName));
                    return CompletableFuture.completedFuture(null);
                }

                return api.getIslandUuid(islandOwnerUuidOpt.get()).thenCompose(islandUuid -> {
                    return api.banPlayer(islandUuid, targetPlayerUuidOpt.get());
                }).thenRun(() -> {
                    sender.sendMessage(config.getAdminBanSuccessMessage(islandOwnerName, banPlayerName));
                    api.sendPlayerMessage(targetPlayerUuidOpt.get(), config.getWasBannedFromIslandMessage(islandOwnerName));
                });
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(islandOwnerName));
            } else if (cause instanceof PlayerAlreadyBannedException) {
                sender.sendMessage(config.getPlayerAlreadyBannedMessage(banPlayerName));
            } else if (cause instanceof CannotBanIslandPlayerException) {
                sender.sendMessage(config.getPlayerCannotBanIslandPlayerMessage());
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error banning player " + banPlayerName + " from island of " + islandOwnerName, ex);
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