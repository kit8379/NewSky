package org.me.newsky.command.sub;

import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public abstract class BaseInfoCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseInfoCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {

        return true;
    }

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);
}
