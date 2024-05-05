package org.me.newsky.command.sub.player;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.sub.BaseInfoCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class PlayerInfoCommand extends BaseInfoCommand {

    public PlayerInfoCommand(ConfigHandler config, NewSkyAPI api) {
        super(config, api);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getPlayerInfoUsageMessage();
    }
}
