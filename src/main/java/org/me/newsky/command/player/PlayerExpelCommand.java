package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.CannotExpelIslandPlayerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /is expel <player>
 */
public class PlayerExpelCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerExpelCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "expel";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerExpelAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerExpelPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerExpelSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerExpelDescription();
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

        api.getOnlinePlayersNames().thenCompose(onlinePlayerNames -> {
            if (!onlinePlayerNames.contains(targetPlayerName)) {
                player.sendMessage(config.getPlayerNotOnlineMessage(targetPlayerName));
                return CompletableFuture.completedFuture(null);
            }

            return api.getPlayerUuid(targetPlayerName).thenCompose(targetUuidOpt -> {
                if (targetUuidOpt.isEmpty()) {
                    player.sendMessage(config.getUnknownPlayerMessage(targetPlayerName));
                    return CompletableFuture.completedFuture(null);
                }

                UUID targetPlayerUuid = targetUuidOpt.get();

                return api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.expelPlayer(islandUuid, targetPlayerUuid).thenRun(() -> player.sendMessage(config.getPlayerExpelSuccessMessage(targetPlayerName))));
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (cause instanceof CannotExpelIslandPlayerException) {
                player.sendMessage(config.getPlayerCannotExpelIslandPlayerMessage());
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Failed to expel player " + targetPlayerName + " from island of player " + player.getName(), ex);
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