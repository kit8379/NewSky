package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.WarpDoesNotExistException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /is delwarp <warpName>
 */
public class PlayerDelWarpCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerDelWarpCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "delwarp";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerDelWarpAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerDelWarpPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerDelWarpSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerDelWarpDescription();
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

        String warpName = args[1];
        UUID playerUuid = player.getUniqueId();

        api.delWarp(playerUuid, warpName).thenRun(() -> {
            player.sendMessage(config.getPlayerDelWarpSuccessMessage(warpName));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (cause instanceof WarpDoesNotExistException) {
                player.sendMessage(config.getPlayerNoWarpMessage(warpName));
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error deleting warp for player " + player.getName(), ex);
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
                return api.getWarpNames(player.getUniqueId()).stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }
}