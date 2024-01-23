package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseSetwarpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class IslandSetwarpCommand extends BaseSetwarpCommand {

    public IslandSetwarpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("§eUsage: §b/island setwarp");
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return "You do not have an island to set a warp point.";
    }

    @Override
    protected String getMustInIslandMessage(String[] args) {
        return "You must be in your island world to set a warp point.";
    }

    @Override
    protected String getSetWarpSuccessMessage(String[] args) {
        return "Warp point set for your island.";
    }
}
