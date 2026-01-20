package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.HomeDoesNotExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /is delhome <homeName>
 */
public class PlayerDelHomeCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerDelHomeCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
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
        return config.getPlayerDelHomeAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerDelHomePermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerDelHomeSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerDelHomeDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        if (args.length < 2) {
            return false;
        }

        String homeName = args[1];

        if ("default".equals(homeName)) {
            player.sendMessage(config.getPlayerCannotDeleteDefaultHomeMessage());
            return true;
        }

        api.delHome(player.getUniqueId(), homeName).thenRun(() -> {
            player.sendMessage(config.getPlayerDelHomeSuccessMessage(homeName));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (cause instanceof HomeDoesNotExistException) {
                player.sendMessage(config.getPlayerNoHomeMessage(homeName));
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error deleting home for player " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2 && sender instanceof Player player) {
            try {
                String prefix = args[1].toLowerCase();
                return api.getHomeNames(player.getUniqueId()).stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }
}