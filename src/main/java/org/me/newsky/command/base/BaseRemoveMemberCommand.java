package org.me.newsky.command.base;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public abstract class BaseRemoveMemberCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseRemoveMemberCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check if the command arguments are valid
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the island owner's UUID
        UUID islandOwnerId = getIslandOwnerUuid(sender, args);

        // Get the target player's UUID
        OfflinePlayer targetRemove = Bukkit.getOfflinePlayer(args[getTargetRemoveArgIndex()]);
        UUID targetUuid = targetRemove.getUniqueId();

        // Remove the target player from the island
        api.playerAPI.removeMember(islandOwnerId, targetUuid).thenRun(() -> {
            sender.sendMessage(getIslandRemoveMemberSuccessMessage(args));
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error removing the member");
            ex.printStackTrace();
            return null;
        });

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract int getTargetRemoveArgIndex();

    protected abstract UUID getIslandOwnerUuid(CommandSender sender, String[] args);

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getIslandRemoveMemberSuccessMessage(String[] args);
}
