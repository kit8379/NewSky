package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;

import java.util.UUID;
import java.util.logging.Level;

/**
 * /isadmin sethome <player> <home>
 */
public class AdminSetHomeCommand implements SubCommand {
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

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(homePlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();
        Location loc = player.getLocation();

        api.setHome(targetUuid, homeName, loc).thenRun(() -> sender.sendMessage(config.getAdminSetHomeSuccessMessage(homePlayerName, homeName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(homePlayerName));
            } else if (cause instanceof LocationNotInIslandException) {
                sender.sendMessage(config.getAdminMustInIslandSetHomeMessage(homePlayerName));
            } else {
                sender.sendMessage("There was an error setting the home.");
                plugin.getLogger().log(Level.SEVERE, "Error setting home " + homeName + " for " + homePlayerName, ex);
            }
            return null;
        });

        return true;
    }
}
