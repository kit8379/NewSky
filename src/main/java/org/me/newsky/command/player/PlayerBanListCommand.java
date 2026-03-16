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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

        api.getIslandUuid(playerUuid).thenCompose(api::getBannedPlayers).thenCompose(bannedPlayers -> {
            if (bannedPlayers.isEmpty()) {
                player.sendMessage(config.getNoBannedPlayersMessage());
                return CompletableFuture.completedFuture(null);
            }

            return api.getPlayerNames(bannedPlayers).thenAccept(nameMap -> {
                List<String> playerNames = bannedPlayers.stream().map(uuid -> nameMap.getOrDefault(uuid, uuid.toString())).sorted(String.CASE_INSENSITIVE_ORDER).toList();

                TextComponent.Builder bannedList = Component.text().append(config.getBannedPlayersHeaderMessage());

                for (String playerName : playerNames) {
                    bannedList.append(Component.text("\n"));
                    bannedList.append(config.getBannedPlayerMessage(playerName));
                }

                player.sendMessage(bannedList.build());
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error retrieving ban list for " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }
}