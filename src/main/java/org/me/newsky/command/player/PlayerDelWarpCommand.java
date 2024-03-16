package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.base.BaseDelWarpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class PlayerDelWarpCommand extends BaseDelWarpCommand {

    public PlayerDelWarpCommand(ConfigHandler config, NewSkyAPI api) {
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
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected int getTargetWarpArgIndex() {
        return 1;
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getPlayerNoIslandMessage();
    }

    @Override
    protected String getNoWarpMessage(String[] args) {
        return config.getPlayerNoWarpMessage(args[1]);
    }

    @Override
    protected String getDelWarpSuccessMessage(String[] args) {
        return config.getPlayerDelWarpSuccessMessage(args[1]);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getPlayerDelWarpUsageMessage();
    }
}
