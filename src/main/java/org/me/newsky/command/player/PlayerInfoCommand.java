package org.me.newsky.command.player;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.base.BaseInfoCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class PlayerInfoCommand extends BaseInfoCommand {

    public PlayerInfoCommand(ConfigHandler config, NewSkyAPI api) {
        super(config, api);
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        if (args.length > 1) {
            return Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        } else {
            return ((Player) sender).getUniqueId();
        }
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getPlayerInfoUsageMessage();
    }
}