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
import org.me.newsky.exceptions.PlayerNotBannedException;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * /is unban <player>
 */
public class PlayerUnbanCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerUnbanCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "unban";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerUnbanAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerUnbanPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerUnbanSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerUnbanDescription();
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

        UUID playerUuid = player.getUniqueId();
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetPlayerUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.unbanPlayer(islandUuid, targetPlayerUuid)).thenRun(() -> player.sendMessage(config.getPlayerUnbanSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof PlayerNotBannedException) {
                player.sendMessage(config.getPlayerNotBannedMessage(targetPlayerName));
            } else {
                player.sendMessage("There was an error unbanning the player.");
                plugin.getLogger().log(Level.SEVERE, "Error unbanning player " + targetPlayerName + " from island of player " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2 && sender instanceof Player player) {
            try {
                UUID islandUuid = api.getIslandUuid(player.getUniqueId()).get();
                Set<UUID> banned = api.getBannedPlayers(islandUuid).get();
                String prefix = args[1].toLowerCase();
                return banned.stream().map(uuid -> {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    return op.getName() != null ? op.getName() : uuid.toString();
                }).filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
            } catch (InterruptedException | ExecutionException e) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }
}