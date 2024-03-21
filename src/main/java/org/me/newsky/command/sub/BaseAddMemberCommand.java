package org.me.newsky.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public abstract class BaseAddMemberCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseAddMemberCommand(ConfigHandler config, NewSkyAPI api) {
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
        OfflinePlayer targetAdd = Bukkit.getOfflinePlayer(args[getTargetAddArgIndex()]);
        UUID targetUuid = targetAdd.getUniqueId();

        // Set the member role
        String role = "member";

        // Add the target player to the island
        api.playerAPI.addMember(islandOwnerId, targetUuid, role).thenRun(() -> {
            sender.sendMessage(getIslandAddMemberSuccessMessage(args));
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error adding the member");
            ex.printStackTrace();
            return null;
        });

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract int getTargetAddArgIndex();

    protected abstract UUID getIslandOwnerUuid(CommandSender sender, String[] args);

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getIslandAddMemberSuccessMessage(String[] args);
}
