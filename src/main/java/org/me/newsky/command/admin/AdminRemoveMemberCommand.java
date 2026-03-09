package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /isadmin removemember <member> <owner>
 */
public class AdminRemoveMemberCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminRemoveMemberCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
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
        return config.getAdminRemoveMemberAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminRemoveMemberPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminRemoveMemberSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminRemoveMemberDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String targetMemberName = args[1];
        String islandOwnerName = args[2];

        api.getPlayerUuid(targetMemberName).thenCompose(targetMemberUuidOpt -> {
            if (targetMemberUuidOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(targetMemberName));
                return CompletableFuture.completedFuture(null);
            }

            return api.getPlayerUuid(islandOwnerName).thenCompose(islandOwnerUuidOpt -> {
                if (islandOwnerUuidOpt.isEmpty()) {
                    sender.sendMessage(config.getUnknownPlayerMessage(islandOwnerName));
                    return CompletableFuture.completedFuture(null);
                }

                return api.getIslandUuid(islandOwnerUuidOpt.get()).thenCompose(islandUuid -> api.removeMember(islandUuid, targetMemberUuidOpt.get())).thenRun(() -> {
                    sender.sendMessage(config.getAdminRemoveMemberSuccessMessage(targetMemberName, islandOwnerName));
                    api.sendPlayerMessage(targetMemberUuidOpt.get(), config.getWasRemovedFromIslandMessage(islandOwnerName));
                });
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(islandOwnerName));
            } else if (cause instanceof CannotRemoveOwnerException) {
                sender.sendMessage(config.getCannotRemoveOwnerMessage());
            } else if (cause instanceof IslandPlayerDoesNotExistException) {
                sender.sendMessage(config.getIslandMemberNotExistsMessage(targetMemberName));
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error removing member " + targetMemberName + " from island of " + islandOwnerName, ex);
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
            return api.getOnlinePlayersNames().thenApply(names -> names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList())).exceptionally(ex -> Collections.emptyList());
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}