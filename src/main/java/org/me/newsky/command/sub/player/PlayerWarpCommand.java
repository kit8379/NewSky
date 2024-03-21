package org.me.newsky.command.sub.player;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.sub.BaseWarpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class PlayerWarpCommand extends BaseWarpCommand {

    public PlayerWarpCommand(ConfigHandler config, NewSkyAPI api) {
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
        return Bukkit.getOfflinePlayer(args[1]).getUniqueId();
    }

    @Override
    protected int getTargetWarpArgIndex() {
        return 2;
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getPlayerWarpUsageMessage();
    }
}
