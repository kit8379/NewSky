package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.HomeDoesNotExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /isadmin delhome <player> <home>
 */
public class AdminDelHomeCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminDelHomeCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "delhome";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminDelHomeAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminDelHomePermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminDelHomeSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminDelHomeDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String homePlayerName = args[1];
        String homeName = args[2];

        if ("default".equalsIgnoreCase(homeName)) {
            sender.sendMessage(config.getAdminCannotDeleteDefaultHomeMessage(homePlayerName));
            return true;
        }

        api.getPlayerUuid(homePlayerName).thenCompose(targetUuidOpt -> {
            if (targetUuidOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(homePlayerName));
                return CompletableFuture.completedFuture(null);
            }

            return api.delHome(targetUuidOpt.get(), homeName).thenRun(() -> sender.sendMessage(config.getAdminDelHomeSuccessMessage(homePlayerName, homeName)));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();

            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(homePlayerName));
            } else if (cause instanceof HomeDoesNotExistException) {
                sender.sendMessage(config.getAdminNoHomeMessage(homePlayerName, homeName));
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error deleting home " + homeName + " for " + homePlayerName, ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public CompletableFuture<List<String>> tabCompleteAsync(CommandSender sender, String label, String[] args) {
        if (args.length != 3) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return api.getPlayerUuid(args[1]).thenCompose(uuidOpt -> {
            if (uuidOpt.isEmpty()) {
                return CompletableFuture.completedFuture(Collections.<String>emptyList());
            }

            String prefix = args[2].toLowerCase(Locale.ROOT);
            return api.getHomeNames(uuidOpt.get()).thenApply(names -> names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList()));
        }).exceptionally(ex -> Collections.emptyList());
    }
}