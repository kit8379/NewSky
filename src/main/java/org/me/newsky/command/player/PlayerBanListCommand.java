package org.me.newsky.command.player;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
 * /is banlist
 */
public class PlayerBanListCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerBanListCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "banlist";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerBanListAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerBanListPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerBanListSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerBanListDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        UUID playerUuid = player.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(api::getBannedPlayers).thenAccept(bannedPlayers -> {
            if (bannedPlayers.isEmpty()) {
                player.sendMessage(config.getNoBannedPlayersMessage());
                return;
            }

            Component bannedList = config.getBannedPlayersHeaderMessage();
            for (UUID bannedPlayerUuid : bannedPlayers) {
                OfflinePlayer bannedPlayer = Bukkit.getOfflinePlayer(bannedPlayerUuid);
                String playerName = bannedPlayer.getName();
                if (playerName == null) {
                    playerName = bannedPlayerUuid.toString();
                }
                bannedList = bannedList.append(config.getBannedPlayerMessage(playerName));
            }

            player.sendMessage(bannedList);
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                player.sendMessage(Component.text("There was an error retrieving the ban list."));
                plugin.getLogger().log(Level.SEVERE, "Error retrieving ban list for " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }
}
