package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerNotCoopedException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /is uncoop <player>
 */
public class PlayerUncoopCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerUncoopCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "uncoop";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerUncoopAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerUncoopPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerUncoopSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerUncoopDescription();
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

            UUID targetPlayerUuid = targetUuidOpt.get();

            return api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.removeCoop(islandUuid, targetPlayerUuid).thenRun(() -> {
                player.sendMessage(config.getPlayerUncoopSuccessMessage(targetPlayerName));
                api.sendPlayerMessage(targetPlayerUuid, config.getWasUncoopedFromIslandMessage(player.getName()));
            }));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (cause instanceof PlayerNotCoopedException) {
                player.sendMessage(config.getPlayerNotCoopedMessage(targetPlayerName));
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error uncooping player " + targetPlayerName + " for " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public CompletableFuture<List<String>> tabCompleteAsync(CommandSender sender, String label, String[] args) {
        if (args.length != 2 || !(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String prefix = args[1].toLowerCase(Locale.ROOT);
        UUID playerUuid = player.getUniqueId();

        return api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.getCoopedPlayers(islandUuid).thenCompose(coopedPlayers -> api.getPlayerNames(coopedPlayers).thenApply(nameMap -> coopedPlayers.stream().map(uuid -> nameMap.getOrDefault(uuid, uuid.toString())).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList())))).exceptionally(ex -> Collections.emptyList());
    }
}