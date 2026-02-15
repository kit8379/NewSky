// PlayerSetWarpCommand.java
package org.me.newsky.command.player;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;
import org.me.newsky.exceptions.WarpNameNotLegalException;
import org.me.newsky.island.UpgradeHandler;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /is setwarp [warpName]
 */
public class PlayerSetWarpCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerSetWarpCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
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
        return config.getPlayerSetWarpAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerSetWarpPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerSetWarpSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerSetWarpDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        String warpName = (args.length >= 2) ? args[1] : "default";
        UUID playerUuid = player.getUniqueId();

        Location loc = player.getLocation();
        String worldName = loc.getWorld().getName();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(playerUuid);
        } catch (IslandDoesNotExistException ex) {
            player.sendMessage(config.getPlayerNoIslandMessage());
            return true;
        }

        Set<String> existingWarps;
        existingWarps = api.getWarpNames(playerUuid);
        boolean overwriting = existingWarps.stream().anyMatch(n -> n != null && n.equalsIgnoreCase(warpName));
        int warpLimitLevel = api.getCurrentUpgradeLevel(islandUuid, UpgradeHandler.UPGRADE_WARP_LIMIT);
        int warpLimit = api.getWarpLimit(warpLimitLevel);
        if (!overwriting && existingWarps.size() >= warpLimit) {
            player.sendMessage(config.getPlayerWarpLimitReachedMessage(warpLimit));
            return true;
        }

        api.setWarp(playerUuid, warpName, worldName, x, y, z, yaw, pitch).thenRun(() -> player.sendMessage(config.getPlayerSetWarpSuccessMessage(warpName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (cause instanceof LocationNotInIslandException) {
                player.sendMessage(config.getPlayerMustInIslandSetWarpMessage());
            } else if (cause instanceof WarpNameNotLegalException) {
                player.sendMessage(config.getWarpNameNotLegalMessage());
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error setting warp for player " + player.getName(), ex);
            }

            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2 && sender instanceof Player player) {
            try {
                Set<String> warps = api.getWarpNames(player.getUniqueId());
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return warps.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }
}
