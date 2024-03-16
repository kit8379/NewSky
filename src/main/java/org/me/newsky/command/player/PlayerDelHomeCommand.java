package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.base.BaseDelHomeCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class PlayerDelHomeCommand extends BaseDelHomeCommand {

    public PlayerDelHomeCommand(ConfigHandler config, NewSkyAPI api) {
        super(config, api);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(config.getUsagePrefix() + getUsageCommandMessage());
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected int getTargetHomeArgIndex() {
        return 1;
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getPlayerNoIslandMessage();
    }

    @Override
    protected String getCannotDeleteDefaultHomeMessage(String[] args) {
        return config.getPlayerCannotDeleteDefaultHomeMessage();
    }

    @Override
    protected String getNoHomeMessage(String[] args) {
        return config.getPlayerNoHomeMessage(args[1]);
    }

    @Override
    protected String getDelHomeSuccessMessage(String[] args) {
        return config.getPlayerDelHomeSuccessMessage(args[1]);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getPlayerDelHomeUsageMessage();
    }
}
