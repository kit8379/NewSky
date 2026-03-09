package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.CannotCoopIslandPlayerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerAlreadyCoopedException;
import org.me.newsky.island.UpgradeHandler;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /is coop <player>
 */
public class PlayerCoopCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerCoopCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
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
        return config.getPlayerCoopAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerCoopPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerCoopSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerCoopDescription();
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

        String targetPlayerName = args[1];
        UUID playerUuid = player.getUniqueId();

        api.getPlayerUuid(targetPlayerName).thenCompose(targetUuidOpt -> {
            if (targetUuidOpt.isEmpty()) {
                player.sendMessage(config.getUnknownPlayerMessage(targetPlayerName));
                return CompletableFuture.completedFuture(null);
            }

            UUID targetUuid = targetUuidOpt.get();

            return api.getOnlinePlayersNames().thenCompose(onlineNames -> {
                if (!onlineNames.contains(targetPlayerName)) {
                    player.sendMessage(config.getPlayerNotOnlineMessage(targetPlayerName));
                    return CompletableFuture.completedFuture(null);
                }

                return api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.getCurrentUpgradeLevel(islandUuid, UpgradeHandler.UPGRADE_COOP_LIMIT).thenCompose(coopLimitLevel -> {
                    int coopLimit = api.getCoopLimit(coopLimitLevel);

                    return api.getCoopedPlayers(islandUuid).thenCompose(coopedPlayers -> {
                        if (coopedPlayers.size() >= coopLimit) {
                            player.sendMessage(config.getPlayerCoopLimitReachedMessage(coopLimit));
                            return CompletableFuture.completedFuture(null);
                        }

                        return api.addCoop(islandUuid, targetUuid).thenRun(() -> {
                            player.sendMessage(config.getPlayerCoopSuccessMessage(targetPlayerName));
                            api.sendPlayerMessage(targetUuid, config.getWasCoopedToIslandMessage(player.getName()));
                        });
                    });
                }));
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (cause instanceof PlayerAlreadyCoopedException) {
                player.sendMessage(config.getPlayerAlreadyCoopedMessage(targetPlayerName));
            } else if (cause instanceof CannotCoopIslandPlayerException) {
                player.sendMessage(config.getPlayerCannotCoopIslandPlayerMessage());
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error cooping player " + targetPlayerName + " for " + player.getName(), ex);
            }

            return null;
        });

        return true;
    }

    @Override
    public CompletableFuture<List<String>> tabCompleteAsync(CommandSender sender, String label, String[] args) {
        if (args.length != 2) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String prefix = args[1].toLowerCase(Locale.ROOT);

        return api.getOnlinePlayersNames().thenApply(names -> names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList())).exceptionally(ex -> Collections.emptyList());
    }
}