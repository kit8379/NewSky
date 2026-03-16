package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandOperationBusyException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.island.UpgradeHandler;
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

        api.getPendingInvite(playerUuid).thenCompose(optionalInvite -> {
            if (optionalInvite.isEmpty()) {
                player.sendMessage(config.getPlayerNoPendingInviteMessage());
                return CompletableFuture.completedFuture(null);
            }

            Invitation invite = optionalInvite.get();
            UUID islandUuid = invite.getIslandUuid();
            UUID inviterUuid = invite.getInviterUuid();

            return api.getCurrentUpgradeLevel(islandUuid, UpgradeHandler.UPGRADE_TEAM_LIMIT).thenCompose(teamLimitLevel -> {
                int teamLimit = api.getTeamLimit(teamLimitLevel);

                return api.getIslandMembers(islandUuid).thenCompose(members -> {
                    if (members.size() >= teamLimit) {
                        player.sendMessage(config.getPlayerTeamLimitReachedMessage(teamLimit));
                        return CompletableFuture.completedFuture(null);
                    }

                    return api.removePendingInvite(playerUuid).thenCompose(v -> {
                        return api.addMember(islandUuid, playerUuid, "member");
                    }).thenCompose(v -> {
                        player.sendMessage(config.getPlayerInviteAcceptedMessage());
                        api.sendPlayerMessage(inviterUuid, config.getPlayerInviteAcceptedNotifyMessage(player.getName()));
                        return api.getIslandMembers(islandUuid);
                    }).thenCompose(membersAfterJoin -> {
                        for (UUID uuid : membersAfterJoin) {
                            if (!uuid.equals(playerUuid) && !uuid.equals(inviterUuid)) {
                                api.sendPlayerMessage(uuid, config.getNewMemberNotificationMessage(player.getName()));
                            }
                        }
                        return api.home(playerUuid, "default", playerUuid);
                    });
                });
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandOperationBusyException) {
                sender.sendMessage(config.getIslandBusyMessage());
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