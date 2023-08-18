package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;

public class AdminDeleteCommand implements IslandSubCommand {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final IslandHandler islandHandler;

    public AdminDeleteCommand(NewSky plugin, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /islandadmin delete <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(target.getUniqueId());

        // Check if player have an island
        if (islandUuid.isEmpty()) {
            sender.sendMessage(target.getName() + " don't have an island.");
            return true;
        }

        // Delete island
        islandHandler.deleteWorld(islandUuid.get().toString());
        cacheHandler.deleteIsland(islandUuid.get());
        sender.sendMessage("Deleted " + target.getName() + " island.");

        return true;
    }
}