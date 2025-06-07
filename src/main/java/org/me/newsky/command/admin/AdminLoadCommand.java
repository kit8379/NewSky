package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.NoActiveServerException;

import java.util.UUID;
import java.util.logging.Level;

/**
 * /isadmin load <player>
 */
public class AdminLoadCommand implements SubCommand {
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

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(targetUuid);
        } catch (IslandDoesNotExistException e) {
            sender.sendMessage(config.getNoIslandMessage(targetPlayerName));
            return true;
        }

        api.loadIsland(islandUuid).thenRun(() -> sender.sendMessage(config.getIslandLoadSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else if (cause instanceof IslandAlreadyLoadedException) {
                sender.sendMessage(config.getIslandAlreadyLoadedMessage());
            } else {
                sender.sendMessage("There was an error loading the island.");
                plugin.getLogger().log(Level.SEVERE, "Error loading island for " + targetPlayerName, ex);
            }
            return null;
        });

        return true;
    }
}