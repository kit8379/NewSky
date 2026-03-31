package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /isadmin warp <player> [warp] [target]
 */
public class AdminWarpCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminWarpCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "warp";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminWarpAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminWarpPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminWarpSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminWarpDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }

        String warpPlayerName = args[1];
        String warpName = args.length >= 3 ? args[2] : "default";
        String teleportPlayerName = args.length >= 4 ? args[3] : null;

        UUID senderUuid = null;
        if (teleportPlayerName == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
                return true;
            }
            senderUuid = player.getUniqueId();
        }

        UUID finalSenderUuid = senderUuid;

        api.getPlayerUuid(warpPlayerName).thenCompose(warpPlayerUuidOpt -> {
            if (warpPlayerUuidOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(warpPlayerName));
                return CompletableFuture.completedFuture(null);
            }

            UUID warpPlayerUuid = warpPlayerUuidOpt.get();

            return api.getIslandUuid(warpPlayerUuid).thenCompose(islandUuid -> {
                if (teleportPlayerName == null) {
                    return api.warp(islandUuid, warpPlayerUuid, warpName, finalSenderUuid).thenRun(() -> {
                        api.sendPlayerMessage(finalSenderUuid, config.getWarpSuccessMessage(warpPlayerName, warpName));
                    });
                }

                return api.getPlayerUuid(teleportPlayerName).thenCompose(teleportPlayerUuidOpt -> {
                    if (teleportPlayerUuidOpt.isEmpty()) {
                        sender.sendMessage(config.getUnknownPlayerMessage(teleportPlayerName));
                        return CompletableFuture.completedFuture(null);
                    }

                    UUID teleportPlayerUuid = teleportPlayerUuidOpt.get();

                    return api.warp(islandUuid, warpPlayerUuid, warpName, teleportPlayerUuid).thenRun(() -> api.sendPlayerMessage(teleportPlayerUuid, config.getWarpSuccessMessage(warpPlayerName, warpName)));
                });
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getNoIslandMessage(warpPlayerName));
            } else if (cause instanceof WarpDoesNotExistException) {
                sender.sendMessage(config.getNoWarpMessage(warpPlayerName, warpName));
            } else if (cause instanceof PlayerBannedException) {
                sender.sendMessage(config.getPlayerBannedMessage());
            } else if (cause instanceof IslandLockedException) {
                sender.sendMessage(config.getIslandLockedMessage());
            } else if (cause instanceof IslandOperationBusyException) {
                sender.sendMessage(config.getIslandBusyMessage());
            } else if (cause instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error teleporting to warp " + warpName + " of " + warpPlayerName, ex);
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
            return api.getPlayerUuid(args[1]).thenCompose(ownerUuidOpt -> {
                if (ownerUuidOpt.isEmpty()) {
                    return CompletableFuture.completedFuture(Collections.<String>emptyList());
                }

                UUID ownerUuid = ownerUuidOpt.get();

                return api.getIslandUuid(ownerUuid).thenCompose(islandUuid -> api.getWarpNames(islandUuid, ownerUuid).thenApply(warps -> warps.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList())));
            }).exceptionally(ex -> Collections.emptyList());
        }

        if (args.length == 4) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return api.getOnlinePlayersNames().thenApply(names -> names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList())).exceptionally(ex -> Collections.emptyList());
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}