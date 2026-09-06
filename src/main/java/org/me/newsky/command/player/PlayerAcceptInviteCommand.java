package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.IslandPlayerAlreadyExistsException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.model.Invitation;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /is accept
 */
public class PlayerAcceptInviteCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerAcceptInviteCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "accept";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerAcceptAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerAcceptPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerAcceptSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerAcceptDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        UUID playerUuid = player.getUniqueId();

        // Invitation redemption is hidden behind the player-scoped API, so commands do not
        // assemble membership writes themselves.
        api.player(playerUuid).acceptInvite().thenCompose(optionalInvite -> {
            if (optionalInvite.isEmpty()) {
                player.sendMessage(config.getPlayerNoPendingInviteMessage());
                return CompletableFuture.completedFuture(null);
            }

            Invitation invite = optionalInvite.get();
            UUID islandUuid = invite.getIslandUuid();
            UUID inviterUuid = invite.getInviterUuid();

            player.sendMessage(config.getPlayerInviteAcceptedMessage());
            api.sendPlayerMessage(inviterUuid, config.getPlayerInviteAcceptedNotifyMessage(player.getName()));

            return api.getIslandPlayers(islandUuid).thenCompose(islandPlayers -> {
                for (UUID uuid : islandPlayers) {
                    if (!uuid.equals(playerUuid) && !uuid.equals(inviterUuid)) {
                        api.sendPlayerMessage(uuid, config.getNewMemberNotificationMessage(player.getName()));
                    }
                }
                return api.player(playerUuid).home("default");
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandAlreadyExistException) {
                player.sendMessage(config.getPlayerAlreadyHasIslandMessage());
            } else if (cause instanceof IslandPlayerAlreadyExistsException) {
                player.sendMessage(config.getIslandMemberExistsMessage(player.getName()));
            } else if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoPendingInviteMessage());
            } else if (cause instanceof NoActiveServerException) {
                player.sendMessage(config.getNoActiveServerMessage());
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error accepting invite or teleporting for player " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }
}
