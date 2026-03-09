package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;

/**
 * /is top
 */
public class PlayerTopCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerTopCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "top";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerTopAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerTopPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerTopSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerTopDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // TODO: Implement this command
        return true;
    }
}
