package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.model.Invitation;

import java.util.Optional;
import java.util.UUID;

/**
 * /is reject
 */
public class PlayerRejectInviteCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerRejectInviteCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "reject";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerRejectAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerRejectPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerRejectSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerRejectDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        UUID playerUuid = player.getUniqueId();
        Optional<Invitation> optionalInvite = api.getPendingInvite(playerUuid);

        if (optionalInvite.isEmpty()) {
            player.sendMessage(config.getPlayerNoPendingInviteMessage());
            return true;
        }

        Invitation invite = optionalInvite.get();
        UUID inviterUuid = invite.getInviterUuid();

        api.removePendingInvite(playerUuid).thenRun(() -> {
            player.sendMessage(config.getPlayerInviteRejectedMessage());
            api.sendMessage(inviterUuid, config.getPlayerInviteRejectedNotifyMessage(player.getName()));
        }).exceptionally(ex -> {
            player.sendMessage(config.getUnknownExceptionMessage());
            plugin.severe("Error rejecting invite for player " + player.getName(), ex);
            return null;
        });

        return true;
    }
}