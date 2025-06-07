package org.me.newsky.command.player;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.CannotCoopIslandPlayerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerAlreadyCoopedException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * /is coop <player>
 */
public class PlayerCoopCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerCoopCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "coop";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerCoopAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerCoopPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerCoopSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerCoopDescription();
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

        if (playerUuid.equals(targetPlayerUuid)) {
            player.sendMessage(Component.text("You cannot coop yourself."));
            return true;
        }

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(playerUuid);
        } catch (IslandDoesNotExistException ex) {
            player.sendMessage(config.getPlayerNoIslandMessage());
            return true;
        }

        api.addCoop(islandUuid, targetPlayerUuid).thenRun(() -> player.sendMessage(config.getPlayerCoopSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof PlayerAlreadyCoopedException) {
                player.sendMessage(config.getPlayerAlreadyCoopedMessage(targetPlayerName));
            } else if (cause instanceof CannotCoopIslandPlayerException) {
                player.sendMessage(config.getPlayerCannotCoopIslandPlayerMessage());
            } else {
                player.sendMessage(Component.text("There was an error cooping the player."));
                plugin.getLogger().log(Level.SEVERE, "Error cooping player " + targetPlayerName + " for " + player.getName(), ex);
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