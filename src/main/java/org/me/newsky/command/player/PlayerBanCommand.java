package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /is ban <player>
 */
public class PlayerBanCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerBanCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
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
        return config.getPlayerBanAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerBanPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerBanSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerBanDescription();
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

        String targetNameInput = args[1];
        UUID playerUuid = player.getUniqueId();

        api.getPlayerUuid(targetNameInput).thenCompose(targetUuidOpt -> {
            if (targetUuidOpt.isEmpty()) {
                player.sendMessage(config.getUnknownPlayerMessage(targetNameInput));
                return CompletableFuture.completedFuture(null);
            }

            UUID targetUuid = targetUuidOpt.get();

            return api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.banPlayer(islandUuid, targetUuid)).thenRun(() -> {
                player.sendMessage(config.getPlayerBanSuccessMessage(targetNameInput));
                api.sendPlayerMessage(targetUuid, config.getWasBannedFromIslandMessage(player.getName()));
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (cause instanceof PlayerAlreadyBannedException) {
                player.sendMessage(config.getPlayerAlreadyBannedMessage(targetNameInput));
            } else if (cause instanceof CannotBanIslandPlayerException) {
                player.sendMessage(config.getPlayerCannotBanIslandPlayerMessage());
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error banning player " + targetNameInput + " from island of " + player.getName(), ex);
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