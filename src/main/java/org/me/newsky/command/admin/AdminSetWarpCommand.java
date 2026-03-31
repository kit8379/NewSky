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
import org.me.newsky.exceptions.WarpNameNotLegalException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /isadmin setwarp <player> <warp>
 */
public class AdminSetWarpCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminSetWarpCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "setwarp";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminSetWarpAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminSetWarpPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminSetWarpSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminSetWarpDescription();
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

        String warpPlayerName = args[1];
        String warpName = args[2];

        Location loc = player.getLocation();
        String worldName = loc.getWorld().getName();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();

        api.getPlayerUuid(warpPlayerName).thenCompose(targetUuidOpt -> {
            if (targetUuidOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(warpPlayerName));
                return CompletableFuture.completedFuture(null);
            }

            UUID targetUuid = targetUuidOpt.get();

            return api.getIslandUuid(targetUuid).thenCompose(islandUuid -> api.setWarp(islandUuid, targetUuid, warpName, worldName, x, y, z, yaw, pitch).thenRun(() -> sender.sendMessage(config.getAdminSetWarpSuccessMessage(warpPlayerName, warpName))));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(warpPlayerName));
            } else if (cause instanceof LocationNotInIslandException) {
                sender.sendMessage(config.getAdminMustInIslandSetWarpMessage(warpPlayerName));
            } else if (cause instanceof WarpNameNotLegalException) {
                sender.sendMessage(config.getWarpNameNotLegalMessage());
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error setting warp " + warpName + " for " + warpPlayerName, ex);
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

                UUID targetUuid = uuidOpt.get();

                return api.getIslandUuid(targetUuid).thenCompose(islandUuid -> api.getWarpNames(islandUuid, targetUuid).thenApply(warps -> warps.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList())));
            }).exceptionally(ex -> Collections.emptyList());
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}