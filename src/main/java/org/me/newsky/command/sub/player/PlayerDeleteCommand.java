package org.me.newsky.command.sub.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.sub.BaseDeleteCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class PlayerDeleteCommand extends BaseDeleteCommand {

    public PlayerDeleteCommand(ConfigHandler config, NewSkyAPI api) {
        super(config, api);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getPlayerNoIslandMessage();
    }

    @Override
    protected String getIslandDeleteWarningMessage(String[] args) {
        return config.getPlayerDeleteWarningMessage();
    }

    @Override
    protected String getIslandDeleteSuccessMessage(String[] args) {
        return config.getPlayerDeleteSuccessMessage();
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getPlayerDeleteUsageMessage();
    }
}