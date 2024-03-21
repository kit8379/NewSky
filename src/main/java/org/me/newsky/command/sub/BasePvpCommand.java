package org.me.newsky.command.sub;

import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public abstract class BasePvpCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BasePvpCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the target UUID
        UUID targetUuid = getTargetUuid(sender, args);

        // Toggle the island PvP status
        api.islandAPI.toggleIslandPvp(targetUuid).thenAccept(isPvpEnabled -> {
            if (isPvpEnabled) {
                sender.sendMessage(getIslandPvpEnableSuccessMessage(args));
            } else {
                sender.sendMessage(getIslandPvPDisableSuccessMessage(args));
            }
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error toggling the PvP status");
            ex.printStackTrace();
            return null;
        });

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getIslandPvpEnableSuccessMessage(String[] args);

    protected abstract String getIslandPvPDisableSuccessMessage(String[] args);
}
