package org.me.newsky.command.admin;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /isadmin sethome <player> <home>
 */
public class AdminSetHomeCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminSetHomeCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "sethome";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminSetHomeAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminSetHomePermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminSetHomeSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminSetHomeDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        if (args.length < 3) {
            return false;
        }

        String homePlayerName = args[1];
        String homeName = args[2];

        Location loc = player.getLocation();
        String worldName = loc.getWorld().getName();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();

        api.getPlayerUuid(homePlayerName).thenCompose(targetUuidOpt -> {
            if (targetUuidOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(homePlayerName));
                return CompletableFuture.completedFuture(null);
            }

            return api.setHome(targetUuidOpt.get(), homeName, worldName, x, y, z, yaw, pitch).thenRun(() -> sender.sendMessage(config.getAdminSetHomeSuccessMessage(homePlayerName, homeName)));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(homePlayerName));
            } else if (cause instanceof LocationNotInIslandException) {
                sender.sendMessage(config.getAdminMustInIslandSetHomeMessage(homePlayerName));
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error setting home " + homeName + " for " + homePlayerName, ex);
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

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}