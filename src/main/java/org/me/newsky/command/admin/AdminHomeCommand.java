package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.HomeDoesNotExistException;
import org.me.newsky.exceptions.IslandBusyException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.NoActiveServerException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /isadmin home <player> [home] [target]
 */
public class AdminHomeCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminHomeCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "home";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminHomeAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminHomePermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminHomeSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminHomeDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }

        String homePlayerName = args[1];
        String homeName = args.length >= 3 ? args[2] : "default";
        String teleportPlayerName = args.length >= 4 ? args[3] : null;

        if (teleportPlayerName == null && !(sender instanceof Player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        api.getPlayerUuid(homePlayerName).thenCompose(homePlayerUuidOpt -> {
            if (homePlayerUuidOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(homePlayerName));
                return CompletableFuture.completedFuture(null);
            }

            CompletableFuture<UUID> teleportPlayerUuidFuture;
            if (teleportPlayerName == null) {
                teleportPlayerUuidFuture = CompletableFuture.completedFuture(((Player) sender).getUniqueId());
            } else {
                teleportPlayerUuidFuture = api.getPlayerUuid(teleportPlayerName).thenCompose(teleportPlayerUuidOpt -> {
                    if (teleportPlayerUuidOpt.isEmpty()) {
                        sender.sendMessage(config.getUnknownPlayerMessage(teleportPlayerName));
                        return CompletableFuture.completedFuture(null);
                    }
                    return CompletableFuture.completedFuture(teleportPlayerUuidOpt.get());
                });
            }

            return teleportPlayerUuidFuture.thenCompose(teleportPlayerUuid -> {
                if (teleportPlayerUuid == null) {
                    return CompletableFuture.completedFuture(null);
                }

                return api.home(homePlayerUuidOpt.get(), homeName, teleportPlayerUuid).thenRun(() -> api.sendPlayerMessage(teleportPlayerUuid, config.getAdminHomeSuccessMessage(homePlayerName, homeName)));
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(homePlayerName));
            } else if (cause instanceof HomeDoesNotExistException) {
                sender.sendMessage(config.getAdminNoHomeMessage(homePlayerName, homeName));
            } else if (cause instanceof IslandBusyException) {
                sender.sendMessage(config.getIslandBusyMessage());
            } else if (cause instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error teleporting to home " + homeName + " of " + homePlayerName, ex);
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
            return api.getPlayerUuid(args[1]).thenCompose(uuidOpt -> {
                if (uuidOpt.isEmpty()) {
                    return CompletableFuture.completedFuture(Collections.<String>emptyList());
                }

                return api.getHomeNames(uuidOpt.get()).thenApply(homes -> homes.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList()));
            }).exceptionally(ex -> Collections.emptyList());
        }

        if (args.length == 4) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return api.getOnlinePlayersNames().thenApply(names -> names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList())).exceptionally(ex -> Collections.emptyList());
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}