package org.me.newsky.command.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

        try {
            UUID islandUuid = api.getIslandUuid(playerUuid);
            Set<UUID> coopedPlayers = api.getCoopedPlayers(islandUuid);

            if (coopedPlayers.isEmpty()) {
                player.sendMessage(config.getNoCoopedPlayersMessage());
                return true;
            }

            TextComponent.Builder coopedList = Component.text().append(config.getCoopedPlayersHeaderMessage());

            for (UUID coopedPlayerUuid : coopedPlayers) {
                Optional<String> nameOpt = api.getPlayerName(coopedPlayerUuid);
                String playerName = nameOpt.orElse(coopedPlayerUuid.toString());

                coopedList.append(Component.text("\n"));
                coopedList.append(config.getCoopedPlayerMessage(playerName));
            }

            player.sendMessage(coopedList.build());

        } catch (IslandDoesNotExistException ex) {
            player.sendMessage(config.getPlayerNoIslandMessage());
        } catch (Exception ex) {
            player.sendMessage(config.getUnknownExceptionMessage());
            plugin.severe("Error retrieving coop list for " + player.getName(), ex);
        }

        return true;
    }
}