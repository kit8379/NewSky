package org.me.newsky.command.sub.player;

import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.sub.BaseLeaveCommand;
import org.me.newsky.config.ConfigHandler;

public class PlayerLeaveCommand extends BaseLeaveCommand {

    public PlayerLeaveCommand(ConfigHandler config, NewSkyAPI api) {
        super(config, api);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getPlayerLeaveUsageMessage();
    }
}
