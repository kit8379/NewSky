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
import org.me.newsky.exceptions.CannotBanIslandPlayerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerAlreadyBannedException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * /is ban <player>
 */
public class PlayerBanCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerBanCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "ban";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerBanAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerBanPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerBanSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerBanDescription();
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

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(playerUuid);
        } catch (IslandDoesNotExistException e) {
            player.sendMessage(config.getPlayerNoIslandMessage());
            return true;
        }

        api.banPlayer(islandUuid, targetPlayerUuid).thenRun(() -> {
            player.sendMessage(config.getPlayerBanSuccessMessage(targetPlayerName));
            api.sendPlayerMessage(targetPlayerUuid, config.getWasBannedFromIslandMessage(player.getName()));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof PlayerAlreadyBannedException) {
                player.sendMessage(config.getPlayerAlreadyBannedMessage(targetPlayerName));
            } else if (cause instanceof CannotBanIslandPlayerException) {
                player.sendMessage(config.getPlayerCannotBanIslandPlayerMessage());
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.getLogger().log(Level.SEVERE, "Error banning player " + targetPlayerName + " from island of player " + player.getName(), ex);
            }
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