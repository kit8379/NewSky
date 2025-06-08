package org.me.newsky.command.player;

import org.bukkit.Bukkit;
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
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * /is info [player]
 */
public class PlayerInfoCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerInfoCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerInfoAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerInfoPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerInfoSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerInfoDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        UUID playerUuid = (args.length < 2) ? player.getUniqueId() : Bukkit.getOfflinePlayer(args[1]).getUniqueId();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UUID islandUuid = api.getIslandUuid(playerUuid);
                UUID ownerUuid = api.getIslandOwner(islandUuid);
                Set<UUID> members = api.getIslandMembers(islandUuid);
                int level = api.getIslandLevel(islandUuid);

                String ownerName = Bukkit.getOfflinePlayer(ownerUuid).getName();
                String memberNames = members.stream().map(uuid -> {
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    return (name != null) ? name : uuid.toString();
                }).collect(Collectors.joining(", "));

                sender.sendMessage(config.getIslandInfoHeaderMessage());
                sender.sendMessage(config.getIslandInfoUUIDMessage(islandUuid));
                sender.sendMessage(config.getIslandInfoOwnerMessage(ownerName));
                if (memberNames.isEmpty()) {
                    sender.sendMessage(config.getIslandInfoNoMembersMessage());
                } else {
                    sender.sendMessage(config.getIslandInfoMembersMessage(memberNames));
                }
                sender.sendMessage(config.getIslandInfoLevelMessage(level));

            } catch (IslandDoesNotExistException ex) {
                sender.sendMessage(config.getPlayerNoIslandMessage());
            } catch (Exception ex) {
                sender.sendMessage("There was an error getting the island information.");
                plugin.getLogger().log(Level.SEVERE, "Error getting island information for player " + player.getName(), ex);
            }
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