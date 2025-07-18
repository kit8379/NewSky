package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.NoActiveServerException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /is delete
 */
public class PlayerDeleteIslandCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;
    private final Map<UUID, Long> confirmationTimes = new HashMap<>();

    public PlayerDeleteIslandCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
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
        return config.getPlayerDeleteAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerDeletePermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerDeleteSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerDeleteDescription();
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

        if (!confirmationTimes.containsKey(playerUuid) || System.currentTimeMillis() - confirmationTimes.get(playerUuid) >= 15000) {
            confirmationTimes.put(playerUuid, System.currentTimeMillis());
            player.sendMessage(config.getPlayerDeleteWarningMessage());
            return true;
        }

        confirmationTimes.remove(playerUuid);

        api.deleteIsland(islandUuid).thenRun(() -> player.sendMessage(config.getPlayerDeleteSuccessMessage())).exceptionally(ex -> {
            if (ex.getCause() instanceof NoActiveServerException) {
                player.sendMessage(config.getNoActiveServerMessage());
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error deleting island for player " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }
}