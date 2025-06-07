package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.NoActiveServerException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /isadmin delete <player>
 */
public class AdminDeleteIslandCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;
    private final Map<UUID, Long> confirmationTimes = new HashMap<>();

    public AdminDeleteIslandCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "delete";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminDeleteAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminDeletePermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminDeleteSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminDeleteDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }

        String targetPlayerName = args[1];
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        if (!confirmationTimes.containsKey(targetUuid) || System.currentTimeMillis() - confirmationTimes.get(targetUuid) >= 15000) {
            confirmationTimes.put(targetUuid, System.currentTimeMillis());
            sender.sendMessage(config.getAdminDeleteWarningMessage(targetPlayerName));
            return true;
        }
        confirmationTimes.remove(targetUuid);

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(targetUuid);
        } catch (IslandDoesNotExistException e) {
            sender.sendMessage(config.getAdminNoIslandMessage(targetPlayerName));
            return true;
        }

        api.deleteIsland(islandUuid).thenRun(() -> sender.sendMessage(config.getAdminDeleteSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else {
                sender.sendMessage("There was an error deleting the island");
                plugin.getLogger().log(Level.SEVERE, "Error deleting island for " + targetPlayerName, ex);
            }
            return null;
        });

        return true;
    }
}