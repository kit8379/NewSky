package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
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
 * /isadmin uncoop <owner> <player>
 */
public class AdminUncoopCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminUncoopCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
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
        return config.getAdminUncoopAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminUncoopPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminUncoopSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminUncoopDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String ownerName = args[1];
        String targetName = args[2];

        api.getPlayerUuid(ownerName).thenCompose(ownerUuidOpt -> {
            if (ownerUuidOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(ownerName));
                return CompletableFuture.completedFuture(null);
            }

            UUID ownerUuid = ownerUuidOpt.get();

            return api.getPlayerUuid(targetName).thenCompose(targetUuidOpt -> {
                if (targetUuidOpt.isEmpty()) {
                    sender.sendMessage(config.getUnknownPlayerMessage(targetName));
                    return CompletableFuture.completedFuture(null);
                }

                UUID targetUuid = targetUuidOpt.get();

                return api.getIslandUuid(ownerUuid).thenCompose(islandUuid -> api.removeCoop(islandUuid, targetUuid).thenRun(() -> {
                    sender.sendMessage(config.getAdminUncoopSuccessMessage(ownerName, targetName));
                    api.sendPlayerMessage(targetUuid, config.getWasUncoopedFromIslandMessage(ownerName));
                }));
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(ownerName));
            } else if (cause instanceof PlayerNotCoopedException) {
                sender.sendMessage(config.getPlayerNotCoopedMessage(targetName));
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error uncooping player " + targetName + " from island of " + ownerName, ex);
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

                return api.getIslandUuid(ownerUuidOpt.get()).thenCompose(islandUuid -> api.getCoopedPlayers(islandUuid).thenCompose(coopedPlayers -> {
                    if (coopedPlayers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.<String>emptyList());
                    }

                    List<CompletableFuture<String>> nameFutures = coopedPlayers.stream().map(uuid -> api.getPlayerName(uuid).thenApply(nameOpt -> nameOpt.orElse(uuid.toString()))).toList();

                    CompletableFuture<Void> all = CompletableFuture.allOf(nameFutures.toArray(new CompletableFuture[0]));

                    return all.thenApply(v -> nameFutures.stream().map(CompletableFuture::join).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList()));
                }));
            }).exceptionally(ex -> Collections.emptyList());
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}