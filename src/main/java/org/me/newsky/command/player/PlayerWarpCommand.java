package org.me.newsky.command.player;

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
 * /is warp <player> [warpName]
 */
public class PlayerWarpCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerWarpCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
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
        return config.getPlayerWarpAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerWarpPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerWarpSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerWarpDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        if (args.length < 2) {
            return false;
        }

        String targetName = args[1];
        String warpName = args.length >= 3 ? args[2] : "default";

        UUID playerUuid = player.getUniqueId();

        api.getPlayerUuid(targetName).thenCompose(targetUuidOpt -> {
            if (targetUuidOpt.isEmpty()) {
                player.sendMessage(config.getUnknownPlayerMessage(targetName));
                return CompletableFuture.completedFuture(null);
            }

            UUID targetUuid = targetUuidOpt.get();

            return api.warp(targetUuid, warpName, playerUuid).thenRun(() -> api.sendPlayerMessage(playerUuid, config.getWarpSuccessMessage(targetName, warpName)));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getNoIslandMessage(targetName));
            } else if (cause instanceof WarpDoesNotExistException) {
                player.sendMessage(config.getNoWarpMessage(targetName, warpName));
            } else if (cause instanceof PlayerBannedException) {
                player.sendMessage(config.getPlayerBannedMessage());
            } else if (cause instanceof IslandLockedException) {
                player.sendMessage(config.getIslandLockedMessage());
            } else if (cause instanceof IslandBusyException) {
                sender.sendMessage(config.getIslandBusyMessage());
            } else if (cause instanceof NoActiveServerException) {
                player.sendMessage(config.getNoActiveServerMessage());
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error teleporting to warp for player " + player.getName(), ex);
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

            return api.getPlayerUuid(args[1]).thenCompose(targetUuidOpt -> {
                if (targetUuidOpt.isEmpty()) {
                    return CompletableFuture.completedFuture(Collections.<String>emptyList());
                }

                return api.getWarpNames(targetUuidOpt.get()).thenApply(warps -> warps.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList()));
            }).exceptionally(ex -> Collections.emptyList());
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}