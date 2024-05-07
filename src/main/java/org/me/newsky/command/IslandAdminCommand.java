package org.me.newsky.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Description;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.config.ConfigHandler;

@CommandAlias("isadmin")
@Description("Admin commands for island management")
public class IslandAdminCommand extends BaseCommand {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final NewSkyAPI api;

    public IslandAdminCommand(NewSky plugin, ConfigHandler config, NewSkyAPI api) {
        this.plugin = plugin;
        this.config = config;
        this.api = api;
    }
}
