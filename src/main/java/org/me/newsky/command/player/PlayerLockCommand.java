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
 * /is lock
 */
public class PlayerLockCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerLockCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "lock";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerLockAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerLockPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerLockSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerLockDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        UUID playerUuid = player.getUniqueId();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(playerUuid);
        } catch (IslandDoesNotExistException e) {
            player.sendMessage(config.getPlayerNoIslandMessage());
            return true;
        }

        api.toggleIslandLock(islandUuid).thenAccept(isLocked -> {
            if (isLocked) {
                player.sendMessage(config.getPlayerLockSuccessMessage());
            } else {
                player.sendMessage(config.getPlayerUnLockSuccessMessage());
            }
        }).exceptionally(ex -> {
            player.sendMessage(config.getUnknownExceptionMessage());
            plugin.severe("Error toggling island lock status for player " + player.getName(), ex);
            return null;
        });

        return true;
    }
}
