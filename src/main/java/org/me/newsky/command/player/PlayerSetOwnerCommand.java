package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.base.BaseSetOwnerCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.UUID;

public class PlayerSetOwnerCommand extends BaseSetOwnerCommand {

    public PlayerSetOwnerCommand(ConfigHandler config, NewSkyAPI api) {
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
    protected UUID getIslandOwnerUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected int getTargetOwnerArgIndex() {
        return 1;
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getPlayerNoIslandMessage();
    }

    @Override
    protected String getAlreadyOwnerMessage(String[] args) {
        return config.getPlayerAlreadyOwnerMessage(args[1]);
    }

    @Override
    protected String getSetOwnerSuccessMessage(String[] args) {
        return config.getPlayerSetOwnerSuccessMessage(args[1]);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getPlayerSetOwnerUsageMessage();
    }
}
