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
import org.me.newsky.exceptions.IslandPlayerAlreadyExistsException;
import org.me.newsky.exceptions.PlayerAlreadyInAnotherIslandException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * /is addmember <player>
 */
public class PlayerAddMemberCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerAddMemberCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "addmember";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerAddMemberAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerAddMemberPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerAddMemberSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerAddMemberDescription();
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
        } catch (IslandDoesNotExistException e) {
            player.sendMessage(config.getPlayerNoIslandMessage());
            return true;
        }

        api.addMember(islandUuid, targetPlayerUuid, "member").thenRun(() -> player.sendMessage(config.getPlayerAddMemberSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof PlayerAlreadyInAnotherIslandException) {
                player.sendMessage(config.getPlayerAlreadyHasIslandOtherMessage(targetPlayerName));
            } else if (cause instanceof IslandPlayerAlreadyExistsException) {
                player.sendMessage(config.getIslandMemberExistsMessage(targetPlayerName));
            } else {
                player.sendMessage("There was an error adding the member.");
                plugin.getLogger().log(Level.SEVERE, "Error adding member to island for player " + player.getName(), ex);
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
