package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.CannotRemoveOwnerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.IslandPlayerDoesNotExistException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /is removemember <player>
 */
public class PlayerRemoveMemberCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerRemoveMemberCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "removemember";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerRemoveMemberAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerRemoveMemberPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerRemoveMemberSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerRemoveMemberDescription();
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

            if (playerUuid.equals(targetPlayerUuid)) {
                player.sendMessage(config.getPlayerCannotRemoveSelfMessage());
                return CompletableFuture.completedFuture(null);
            }

            return api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.removeMember(islandUuid, targetPlayerUuid).thenRun(() -> {
                player.sendMessage(config.getPlayerRemoveMemberSuccessMessage(targetPlayerName));
                api.sendPlayerMessage(targetPlayerUuid, config.getWasRemovedFromIslandMessage(player.getName()));
            }));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (cause instanceof CannotRemoveOwnerException) {
                player.sendMessage(config.getCannotRemoveOwnerMessage());
            } else if (cause instanceof IslandPlayerDoesNotExistException) {
                player.sendMessage(config.getIslandMemberNotExistsMessage(targetPlayerName));
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error removing member from island for player " + player.getName(), ex);
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

        return api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.getIslandMembers(islandUuid).thenCompose(members -> api.getPlayerNames(members).thenApply(nameMap -> members.stream().map(uuid -> nameMap.getOrDefault(uuid, uuid.toString())).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList())))).exceptionally(ex -> Collections.emptyList());
    }
}