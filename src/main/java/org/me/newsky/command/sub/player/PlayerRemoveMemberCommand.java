package org.me.newsky.command.sub.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.sub.BaseRemoveMemberCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class PlayerRemoveMemberCommand extends BaseRemoveMemberCommand {

    public PlayerRemoveMemberCommand(ConfigHandler config, NewSkyAPI api) {
        super(config, api);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(config.getUsagePrefix() + getUsageCommandMessage());
            return false;
        }
        return true;
    }

    @Override
    protected int getTargetRemoveArgIndex() {
        return 1;
    }

    @Override
    protected UUID getIslandOwnerUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getPlayerNoIslandMessage();
    }

    @Override
    protected String getIslandRemoveMemberSuccessMessage(String[] args) {
        return config.getPlayerRemoveMemberSuccessMessage(args[1]);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getPlayerRemoveMemberUsageMessage();
    }
}