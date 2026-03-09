package org.me.newsky.command.player;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.util.IslandUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /island info [player]
 */
public class PlayerInfoCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerInfoCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerInfoAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerInfoPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerInfoSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerInfoDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        CompletableFuture<UUID> islandFuture;

        if (args.length < 2) {
            Location loc = player.getLocation();
            String worldName = loc.getWorld().getName();

            if (!IslandUtils.isIslandWorld(worldName)) {
                sender.sendMessage(config.getPlayerInfoNotInIslandMessage());
                return true;
            }

            islandFuture = CompletableFuture.completedFuture(IslandUtils.nameToUUID(worldName));
        } else {
            String targetPlayerName = args[1];

            islandFuture = api.getPlayerUuid(targetPlayerName).thenCompose(targetUuidOpt -> {
                if (targetUuidOpt.isEmpty()) {
                    sender.sendMessage(config.getUnknownPlayerMessage(targetPlayerName));
                    return CompletableFuture.completedFuture(null);
                }

                return api.getIslandUuid(targetUuidOpt.get());
            });
        }

        islandFuture.thenCompose(islandUuid -> {
            if (islandUuid == null) {
                return CompletableFuture.completedFuture(null);
            }

            return api.getIslandOwner(islandUuid).thenCompose(ownerUuid -> api.getIslandMembers(islandUuid).thenCompose(members -> api.getIslandLevel(islandUuid).thenCompose(level -> api.getPlayerName(ownerUuid).thenCompose(ownerNameOpt -> {
                String ownerName = ownerNameOpt.orElse(ownerUuid.toString());

                List<CompletableFuture<String>> nameFutures = new ArrayList<>(members.size());
                for (UUID memberUuid : members) {
                    nameFutures.add(api.getPlayerName(memberUuid).thenApply(memberNameOpt -> memberNameOpt.orElse(memberUuid.toString())));
                }

                CompletableFuture<Void> all = CompletableFuture.allOf(nameFutures.toArray(new CompletableFuture[0]));

                return all.thenAccept(v -> {
                    String memberNames = nameFutures.stream().map(CompletableFuture::join).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", "));

                    sender.sendMessage(config.getIslandInfoHeaderMessage());
                    sender.sendMessage(config.getIslandInfoUUIDMessage(islandUuid));
                    sender.sendMessage(config.getIslandInfoOwnerMessage(ownerName));
                    sender.sendMessage(config.getIslandInfoLevelMessage(level));

                    if (memberNames.isEmpty()) {
                        sender.sendMessage(config.getIslandInfoNoMembersMessage());
                    } else {
                        sender.sendMessage(config.getIslandInfoMembersMessage(memberNames));
                    }
                });
            }))));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error retrieving island info for player " + player.getName(), ex);
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