package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.HomeDoesNotExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.UUID;
import java.util.logging.Level;

/**
 * /isadmin delhome <player> <home>
 */
public class AdminDelHomeCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminDelHomeCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "delhome";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminDelHomeAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminDelHomePermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminDelHomeSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminDelHomeDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String homePlayerName = args[1];
        String homeName = args[2];

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(homePlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        if ("default".equals(homeName)) {
            sender.sendMessage(config.getAdminCannotDeleteDefaultHomeMessage(homePlayerName));
            return true;
        }

        api.delHome(targetUuid, homeName).thenRun(() -> sender.sendMessage(config.getAdminDelHomeSuccessMessage(homePlayerName, homeName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(homePlayerName));
            } else if (ex.getCause() instanceof HomeDoesNotExistException) {
                sender.sendMessage(config.getAdminNoHomeMessage(homePlayerName, homeName));
            } else {
                sender.sendMessage("There was an error deleting the home.");
                plugin.getLogger().log(Level.SEVERE, "Error deleting home " + homeName + " for " + homePlayerName, ex);
            }
            return null;
        });

        return true;
    }
}
