package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.CannotRemoveOwnerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.IslandPlayerDoesNotExistException;

import java.util.UUID;
import java.util.logging.Level;

/**
 * /is leave
 */
public class PlayerLeaveCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerLeaveCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "leave";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerLeaveAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerLeavePermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerLeaveSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerLeaveDescription();
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

        api.removeMember(islandUuid, playerUuid).thenRun(() -> player.sendMessage(config.getPlayerLeaveSuccessMessage())).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof CannotRemoveOwnerException) {
                player.sendMessage(config.getPlayerCannotLeaveAsOwnerMessage());
            } else if (cause instanceof IslandPlayerDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.getLogger().log(Level.SEVERE, "Error leaving island for player " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }
}
