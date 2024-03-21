package org.me.newsky.command.sub.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.sub.BasePvpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class PlayerPvpCommand extends BasePvpCommand {

    public PlayerPvpCommand(ConfigHandler config, NewSkyAPI api) {
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
    protected String getIslandPvpEnableSuccessMessage(String[] args) {
        return config.getPlayerPvpEnableSuccessMessage();
    }

    @Override
    protected String getIslandPvPDisableSuccessMessage(String[] args) {
        return config.getPlayerPvpDisableSuccessMessage();
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getPlayerPvpUsageMessage();
    }
}
