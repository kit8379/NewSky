package org.me.newsky.command.base;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.CannotRemoveOwnerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.MemberDoesNotExistException;

import java.util.UUID;

public abstract class BaseLeaveCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseLeaveCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check if the sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        Player player = (Player) sender;
        UUID playerUuid = player.getUniqueId();

        // Remove the player from the island
        api.playerAPI.removeMember(playerUuid, playerUuid).thenRun(() -> {
            sender.sendMessage(config.getPlayerLeaveSuccessMessage());
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof CannotRemoveOwnerException) {
                sender.sendMessage(config.getPlayerCannotLeaveAsOwnerMessage());
            } else if (ex.getCause() instanceof MemberDoesNotExistException) {
                sender.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                sender.sendMessage("There was an error leaving the island");
                ex.printStackTrace();
            }
            return null;
        });

        return true;
    }
}
