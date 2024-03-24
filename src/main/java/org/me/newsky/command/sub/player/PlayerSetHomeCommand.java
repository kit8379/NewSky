package org.me.newsky.command.sub.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.sub.BaseSetHomeCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class PlayerSetHomeCommand extends BaseSetHomeCommand {

    public PlayerSetHomeCommand(ConfigHandler config, NewSkyAPI api) {
        super(config, api);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(config.getUsagePrefix() + getUsageCommandMessage());
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected int getTargetHomeArgIndex() {
        return 1;
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getPlayerNoIslandMessage();
    }

    @Override
    protected String getMustInIslandMessage(String[] args) {
        return config.getPlayerMustInIslandSetHomeMessage();
    }

    @Override
    protected String getSetHomeSuccessMessage(String[] args, String homeName) {
        return config.getPlayerSetHomeSuccessMessage(homeName);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getPlayerSetHomeUsageMessage();
    }
}