package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerNotBannedException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /isadmin unban <owner> <player>
 */
public class AdminUnbanCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminUnbanCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "unban";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminUnbanAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminUnbanPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminUnbanSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminUnbanDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String islandOwnerName = args[1];
        String banPlayerName = args[2];

        api.getPlayerUuid(islandOwnerName).thenCompose(ownerUuidOpt -> {
            if (ownerUuidOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(islandOwnerName));
                return CompletableFuture.completedFuture(null);
            }

            UUID islandOwnerUuid = ownerUuidOpt.get();

            return api.getPlayerUuid(banPlayerName).thenCompose(targetUuidOpt -> {
                if (targetUuidOpt.isEmpty()) {
                    sender.sendMessage(config.getUnknownPlayerMessage(banPlayerName));
                    return CompletableFuture.completedFuture(null);
                }

                UUID targetPlayerUuid = targetUuidOpt.get();

                return api.getIslandUuid(islandOwnerUuid).thenCompose(islandUuid -> api.unbanPlayer(islandUuid, targetPlayerUuid).thenRun(() -> {
                    sender.sendMessage(config.getAdminUnbanSuccessMessage(islandOwnerName, banPlayerName));
                    api.sendPlayerMessage(targetPlayerUuid, config.getWasUnbannedFromIslandMessage(islandOwnerName));
                }));
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(islandOwnerName));
            } else if (cause instanceof PlayerNotBannedException) {
                sender.sendMessage(config.getPlayerNotBannedMessage(banPlayerName));
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error unbanning player " + banPlayerName + " from island of " + islandOwnerName, ex);
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

                return api.getIslandUuid(ownerUuidOpt.get()).thenCompose(islandUuid -> api.getBannedPlayers(islandUuid).thenCompose(bannedPlayers -> {
                    if (bannedPlayers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.<String>emptyList());
                    }

                    return api.getPlayerNames(bannedPlayers).thenApply(nameMap -> bannedPlayers.stream().map(uuid -> nameMap.getOrDefault(uuid, uuid.toString())).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList()));
                }));
            }).exceptionally(ex -> Collections.emptyList());
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}