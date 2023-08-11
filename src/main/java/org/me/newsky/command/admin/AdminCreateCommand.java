package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;

public class AdminCreateCommand implements IslandSubCommand {
    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final IslandHandler islandHandler;

    public AdminCreateCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
        this.islandHandler = plugin.getIslandHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /islandadmin create <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

        UUID islandUuid = UUID.randomUUID();
        cacheHandler.createIsland(islandUuid, target.getUniqueId());
        islandHandler.createWorld(islandUuid.toString());
        if (target.isOnline()) {
            islandHandler.teleportToSpawn(target.getPlayer(), islandUuid.toString());
        }
        sender.sendMessage("Island created.");
        return true;
    }
}
