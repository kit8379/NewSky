package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.command.BaseHomeCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;

public class IslandHomeCommand extends BaseHomeCommand {

    public IslandHomeCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(config, cacheHandler, islandHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§eUsage: §b/island home [homeName]");
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUUID(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected int getTargetHomeArgIndex() {
        return 1;
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return "§cYou do not have an island";
    }

    @Override
    protected String getNoHomesMessage(String[] args) {
        return "§cYou do not have any island homes";
    }

    @Override
    protected String getNoHomeMessage(String[] args) {
        return "§cYou do not have a home named " + args[1];
    }

    @Override
    protected String getIslandHomeSuccessMessage(String[] args) {
        return "§cTeleported to your island home: " + args[1];
    }
}
