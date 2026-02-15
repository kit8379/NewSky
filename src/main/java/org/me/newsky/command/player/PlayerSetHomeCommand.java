// PlayerSetHomeCommand.java
package org.me.newsky.command.player;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.HomeNameNotLegalException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;
import org.me.newsky.island.UpgradeHandler;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /is sethome [homeName]
 */
public class PlayerSetHomeCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerSetHomeCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
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
        return config.getPlayerSetHomeAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerSetHomePermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerSetHomeSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerSetHomeDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        String homeName = (args.length >= 2) ? args[1] : "default";
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

        Set<String> existingHomes;
        existingHomes = api.getHomeNames(playerUuid);
        boolean overwriting = existingHomes.stream().anyMatch(n -> n != null && n.equalsIgnoreCase(homeName));
        int homeLimitLevel = api.getCurrentUpgradeLevel(islandUuid, UpgradeHandler.UPGRADE_HOME_LIMIT);
        int homeLimit = api.getHomeLimit(homeLimitLevel);
        if (!overwriting && existingHomes.size() >= homeLimit) {
            player.sendMessage(config.getPlayerHomeLimitReachedMessage(homeLimit));
            return true;
        }

        api.setHome(playerUuid, homeName, worldName, x, y, z, yaw, pitch).thenRun(() -> player.sendMessage(config.getPlayerSetHomeSuccessMessage(homeName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (cause instanceof LocationNotInIslandException) {
                player.sendMessage(config.getPlayerMustInIslandSetHomeMessage());
            } else if (cause instanceof HomeNameNotLegalException) {
                player.sendMessage(config.getHomeNameNotLegalMessage());
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error setting home for player " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2 && sender instanceof Player player) {
            try {
                Set<String> homes = api.getHomeNames(player.getUniqueId());
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return homes.stream().filter(name -> name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }
}
