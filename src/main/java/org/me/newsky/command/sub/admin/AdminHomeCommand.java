package org.me.newsky.command.sub.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.sub.BaseHomeCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminHomeCommand extends BaseHomeCommand {

    public AdminHomeCommand(ConfigHandler config, NewSkyAPI api) {
        super(config, api);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
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
    protected int getTargetHomeArgIndex() {
        return 2;
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getAdminNoIslandMessage(args[1]);
    }

    @Override
    protected String getNoHomeMessage(String[] args) {
        return config.getAdminNoHomeMessage(args[1], args[2]);
    }

    @Override
    protected String getIslandHomeSuccessMessage(String homeName) {
        return config.getAdminHomeSuccessMessage(homeName);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getAdminHomeUsageMessage();
    }
}
