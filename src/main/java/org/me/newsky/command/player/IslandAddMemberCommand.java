package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseAddMemberCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class IslandAddMemberCommand extends BaseAddMemberCommand {

    public IslandAddMemberCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §b/island addmember <player>");
            return false;
        }
        return true;
    }

    @Override
    protected int getTargetAddArgIndex() {
        return 1;
    }

    @Override
    protected UUID getIslandOwnerUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return "§cYou do not have an island";
    }

    @Override
    protected String getIslandAddMemberSuccessMessage(String[] args) {
        return "§aAdded " + args[1] + " to your island";
    }
}
