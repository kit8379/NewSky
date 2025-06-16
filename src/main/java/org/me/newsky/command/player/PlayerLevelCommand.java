package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.UUID;

/**
 * /is level
 */
public class PlayerLevelCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerLevelCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "level";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerLevelAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerLevelPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerLevelSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerLevelDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        UUID playerUuid = player.getUniqueId();

        try {
            UUID islandUuid = api.getIslandUuid(playerUuid);
            int level = api.getIslandLevel(islandUuid);
            player.sendMessage(config.getIslandLevelMessage(level));
        } catch (IslandDoesNotExistException ex) {
            player.sendMessage(config.getPlayerNoIslandMessage());
        } catch (Exception ex) {
            player.sendMessage(config.getUnknownExceptionMessage());
            plugin.severe("Error calculating island level for player " + player.getName(), ex);
        }

        return true;
    }
}