package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.UUID;
import java.util.logging.Level;

/**
 * /is pvp
 */
public class PlayerPvpCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerPvpCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "pvp";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerPvpAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerPvpPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerPvpSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerPvpDescription();
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
        } catch (IslandDoesNotExistException ex) {
            player.sendMessage(config.getPlayerNoIslandMessage());
            return true;
        } catch (Exception ex) {
            player.sendMessage("There was an error retrieving your island.");
            plugin.getLogger().log(Level.SEVERE, "Error retrieving island UUID for player " + player.getName(), ex);
            return true;
        }

        api.toggleIslandPvp(islandUuid).thenAccept(isPvpEnabled -> {
            if (isPvpEnabled) {
                player.sendMessage(config.getPlayerPvpEnableSuccessMessage());
            } else {
                player.sendMessage(config.getPlayerPvpDisableSuccessMessage());
            }
        }).exceptionally(ex -> {
            player.sendMessage("There was an error toggling the PvP status.");
            plugin.getLogger().log(Level.SEVERE, "Error toggling PvP status for player " + player.getName(), ex);
            return null;
        });

        return true;
    }
}