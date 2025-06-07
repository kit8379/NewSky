package org.me.newsky.command.player;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * /is expel <player>
 */
public class PlayerExpelCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerExpelCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "expel";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerExpelAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerExpelPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerExpelSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerExpelDescription();
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

        String targetPlayerName = args[1];
        if (!api.getOnlinePlayers().contains(targetPlayerName)) {
            player.sendMessage(config.getPlayerNotOnlineMessage(targetPlayerName));
            return true;
        }

        UUID playerUuid = player.getUniqueId();
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetPlayerUuid = targetPlayer.getUniqueId();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(playerUuid);
        } catch (IslandDoesNotExistException ex) {
            player.sendMessage(config.getPlayerNoIslandMessage());
            return true;
        } catch (Exception ex) {
            player.sendMessage("There was an error checking your island.");
            plugin.getLogger().log(Level.SEVERE, "Error getting island UUID for player " + player.getName(), ex);
            return true;
        }

        api.expelPlayer(islandUuid, targetPlayerUuid).thenRun(() -> sender.sendMessage(config.getPlayerExpelSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            sender.sendMessage("There was an error expelling the player.");
            plugin.getLogger().log(Level.SEVERE, "Error expelling player " + targetPlayerName + " from island of player " + player.getName(), ex);
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return api.getOnlinePlayers().stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}