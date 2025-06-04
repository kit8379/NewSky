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
 * /is cooplist
 */
public class PlayerCoopListCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerCoopListCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "cooplist";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerCoopListAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerCoopListPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerCoopListSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerCoopListDescription();
    }


    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        UUID playerUuid = player.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(api::getCoopedPlayers).thenAccept(coopedPlayers -> {
            if (coopedPlayers.isEmpty()) {
                player.sendMessage(config.getNoCoopedPlayersMessage());
                return;
            }

            Component coopList = config.getCoopedPlayersHeaderMessage();
            for (UUID coopedPlayerUuid : coopedPlayers) {
                OfflinePlayer coopedPlayer = Bukkit.getOfflinePlayer(coopedPlayerUuid);
                String name = coopedPlayer.getName() != null ? coopedPlayer.getName() : coopedPlayerUuid.toString();
                coopList = coopList.append(config.getCoopedPlayerMessage(name));
            }

            player.sendMessage(coopList);
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                player.sendMessage(Component.text("There was an error retrieving the coop list."));
                plugin.getLogger().log(Level.SEVERE, "Error retrieving coop list for " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }
}
