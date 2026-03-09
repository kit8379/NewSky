package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.NoActiveServerException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * /isadmin delete <player>
 */
public class AdminDeleteIslandCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;
    private final Map<UUID, Long> confirmationTimes = new ConcurrentHashMap<>();

    public AdminDeleteIslandCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "delete";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminDeleteAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminDeletePermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminDeleteSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminDeleteDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }

        String targetPlayerName = args[1];

        api.getPlayerUuid(targetPlayerName).thenCompose(targetUuidOpt -> {
            if (targetUuidOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(targetPlayerName));
                return CompletableFuture.completedFuture(null);
            }

            UUID targetUuid = targetUuidOpt.get();
            long now = System.currentTimeMillis();
            Long lastConfirmationTime = confirmationTimes.get(targetUuid);

            if (lastConfirmationTime == null || now - lastConfirmationTime >= 15000L) {
                confirmationTimes.put(targetUuid, now);
                sender.sendMessage(config.getAdminDeleteWarningMessage(targetPlayerName));
                return CompletableFuture.completedFuture(null);
            }

            confirmationTimes.remove(targetUuid);

            return api.getIslandUuid(targetUuid).thenCompose(api::deleteIsland).thenRun(() -> sender.sendMessage(config.getAdminDeleteSuccessMessage(targetPlayerName)));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(targetPlayerName));
            } else if (cause instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error deleting island for " + targetPlayerName, ex);
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

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}