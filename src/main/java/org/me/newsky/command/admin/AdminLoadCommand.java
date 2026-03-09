package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandBusyException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.NoActiveServerException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /isadmin load <player>
 */
public class AdminLoadCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminLoadCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "load";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminLoadAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminLoadPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminLoadSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminLoadDescription();
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

            return api.getIslandUuid(targetUuidOpt.get()).thenCompose(api::loadIsland).thenRun(() -> sender.sendMessage(config.getIslandLoadSuccessMessage(targetPlayerName)));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getNoIslandMessage(targetPlayerName));
            } else if (cause instanceof IslandBusyException) {
                sender.sendMessage(config.getIslandBusyMessage());
            } else if (cause instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else if (cause instanceof IslandAlreadyLoadedException) {
                sender.sendMessage(config.getIslandAlreadyLoadedMessage());
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error loading island for " + targetPlayerName, ex);
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