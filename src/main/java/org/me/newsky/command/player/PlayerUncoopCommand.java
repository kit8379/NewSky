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
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerNotCoopedException;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * /is uncoop <player>
 */
public class PlayerUncoopCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerUncoopCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "uncoop";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerUncoopAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerUncoopPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerUncoopSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerUncoopDescription();
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

        api.removeCoop(islandUuid, targetPlayerUuid).thenRun(() -> player.sendMessage(config.getPlayerUncoopSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            if (ex.getCause() instanceof PlayerNotCoopedException) {
                player.sendMessage(config.getPlayerNotCoopedMessage(targetPlayerName));
            } else {
                player.sendMessage(Component.text("There was an error uncooping the player."));
                plugin.getLogger().log(Level.SEVERE, "Error uncooping player " + targetPlayerName + " for " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2 && sender instanceof Player player) {
            try {
                UUID islandUuid = api.getIslandUuid(player.getUniqueId());
                Set<UUID> coops = api.getCoopedPlayers(islandUuid);
                String prefix = args[1].toLowerCase();
                return coops.stream().map(uuid -> {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    return op.getName() != null ? op.getName() : uuid.toString();
                }).filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
            } catch (IslandDoesNotExistException e) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }
}
