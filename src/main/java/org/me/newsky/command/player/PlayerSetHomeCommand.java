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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
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

        api.setHome(playerUuid, homeName, loc).thenRun(() -> player.sendMessage(config.getPlayerSetHomeSuccessMessage(homeName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (cause instanceof LocationNotInIslandException) {
                player.sendMessage(config.getPlayerMustInIslandSetHomeMessage());
            } else {
                player.sendMessage("There was an error setting the home.");
                plugin.getLogger().log(Level.SEVERE, "Error setting home for player " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2 && sender instanceof Player player) {
            Set<String> homes = api.getHomeNames(player.getUniqueId());
            return homes.stream().filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}