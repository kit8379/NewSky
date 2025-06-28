package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.CannotExpelIslandPlayerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
        UUID playerUuid = player.getUniqueId();

        if (!api.getOnlinePlayers().contains(targetPlayerName)) {
            player.sendMessage(config.getPlayerNotOnlineMessage(targetPlayerName));
            return true;
        }

        Optional<UUID> targetUuidOpt = api.getPlayerUuid(targetPlayerName);
        if (targetUuidOpt.isEmpty()) {
            player.sendMessage(config.getUnknownPlayerMessage(targetPlayerName));
            return true;
        }
        UUID targetPlayerUuid = targetUuidOpt.get();


        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(playerUuid);
        } catch (IslandDoesNotExistException e) {
            player.sendMessage(config.getPlayerNoIslandMessage());
            return true;
        }

        api.expelPlayer(islandUuid, targetPlayerUuid).thenRun(() -> player.sendMessage(config.getPlayerExpelSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof CannotExpelIslandPlayerException) {
                player.sendMessage(config.getPlayerCannotExpelIslandPlayerMessage());
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Failed to expel player " + targetPlayerName + " from island of player " + player.getName(), ex);
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