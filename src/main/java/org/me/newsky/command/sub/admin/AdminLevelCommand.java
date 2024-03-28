package org.me.newsky.command.sub.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.sub.BaseLevelCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminLevelCommand extends BaseLevelCommand {

    public AdminLevelCommand(ConfigHandler config, NewSkyAPI api) {
        super(config, api);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(config.getUsagePrefix() + "Usage: /adminlevel <player>");
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return Bukkit.getOfflinePlayer(args[0]).getUniqueId(); // The target is the player specified in the command arguments
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getAdminNoIslandMessage(args[0]);
    }

    @Override
    protected String getIslandLevelSuccessMessage(int level) {
        return config.getAdminIslandLevelMessage(level); // Assuming there is a method in ConfigHandler for this message
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getAdminLevelUsageMessage();
    }
}
