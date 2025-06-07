package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.NoActiveServerException;

import java.util.UUID;
import java.util.logging.Level;

/**
 * /isadmin create <player>
 */
public class AdminCreateIslandCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminCreateIslandCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "create";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminCreateAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminCreatePermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminCreateSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminCreateDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }

        String targetPlayerName = args[1];

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        api.createIsland(targetUuid).thenRun(() -> sender.sendMessage(config.getAdminCreateSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandAlreadyExistException) {
                sender.sendMessage(config.getAdminAlreadyHasIslandMessage(targetPlayerName));
            } else if (cause instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else {
                sender.sendMessage("There was an error creating the island");
                plugin.getLogger().log(Level.SEVERE, "Error creating island for " + targetPlayerName, ex);
            }
            return null;
        });

        return true;
    }
}