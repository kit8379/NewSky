package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;

public abstract class BaseWarpCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseWarpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        if (!validateArgs(sender, args)) {
            return true;
        }

        Player player = (Player) sender;
        OfflinePlayer targetWarp = Bukkit.getOfflinePlayer(args[1]);
        Optional<String> warpLocationOpt = cacheHandler.getWarpLocation(targetWarp.getUniqueId());

        // Check if the target island has a warp point set
        if (warpLocationOpt.isEmpty()) {
            sender.sendMessage("§c" + targetWarp.getName() + " does not have a warp point set.");
            return true;
        }

        // Parse the warp location and teleport the player
        String warpLocation = warpLocationOpt.get();

        sender.sendMessage("§aTeleported you to " + targetWarp.getName() + "'s warp point.");

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
}
