// AdminSetHomeCommand.java
package org.me.newsky.command.admin;

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

import java.util.*;
import java.util.stream.Collectors;

/**
 * /isadmin sethome <player> <home>
 */
public class AdminSetHomeCommand implements SubCommand, TabComplete {
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
        if (args.length < 3) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        String homePlayerName = args[1];
        String homeName = args[2];

        Optional<UUID> targetUuidOpt = api.getPlayerUuid(homePlayerName);
        if (targetUuidOpt.isEmpty()) {
            sender.sendMessage(config.getUnknownPlayerMessage(homePlayerName));
            return true;
        }

        UUID targetUuid = targetUuidOpt.get();

        Location loc = player.getLocation();
        String worldName = loc.getWorld().getName();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();

        api.setHome(targetUuid, homeName, worldName, x, y, z, yaw, pitch).thenRun(() -> sender.sendMessage(config.getAdminSetHomeSuccessMessage(homePlayerName, homeName))).exceptionally(ex -> {
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
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return api.getOnlinePlayersNames().stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }

        if (args.length == 3) {
            Optional<UUID> uuidOpt = api.getPlayerUuid(args[1]);
            if (uuidOpt.isEmpty()) return Collections.emptyList();

            Set<String> homes = api.getHomeNames(uuidOpt.get());
            String prefix = args[2].toLowerCase();
            return homes.stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
