package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.HomeDoesNotExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.NoActiveServerException;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /isadmin home <player> [home] [target]
 */
public class AdminHomeCommand implements SubCommand, TabComplete {
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
        String homeName = (args.length >= 3) ? args[2] : "default";
        String teleportPlayerName = (args.length >= 4) ? args[3] : null;

        OfflinePlayer homePlayer = Bukkit.getOfflinePlayer(homePlayerName);
        UUID homePlayerUuid = homePlayer.getUniqueId();
        UUID teleportPlayerUuid;

        if (teleportPlayerName == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
                return true;
            }
            teleportPlayerUuid = player.getUniqueId();
        } else {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(teleportPlayerName);
            teleportPlayerUuid = targetPlayer.getUniqueId();
        }

        api.home(homePlayerUuid, homeName, teleportPlayerUuid).thenRun(() -> sender.sendMessage(config.getAdminHomeSuccessMessage(homePlayerName, homeName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(homePlayerName));
            } else if (cause instanceof HomeDoesNotExistException) {
                sender.sendMessage(config.getAdminNoHomeMessage(homePlayerName, homeName));
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
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return api.getOnlinePlayers().stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }

        if (args.length == 3) {
            try {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                Set<String> homes = api.getHomeNames(target.getUniqueId());
                String prefix = args[2].toLowerCase();
                return homes.stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }

        if (args.length == 4) {
            String prefix = args[3].toLowerCase();
            return api.getOnlinePlayers().stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}