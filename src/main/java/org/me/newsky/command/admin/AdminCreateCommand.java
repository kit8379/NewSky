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

    public AdminCreateCommand(NewSky plugin, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /islandadmin create <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

        // Check if player already have an island
        if(cacheHandler.getIslandUuidByPlayerUuid(target.getUniqueId()).isPresent()) {
            sender.sendMessage("Player " + target.getName() + " already have an island.");
            return true;
        }

        // Generate island UUID
        UUID islandUuid = UUID.randomUUID();

        // Create island
        islandHandler.createWorld(islandUuid.toString());
        cacheHandler.createIsland(islandUuid, target.getUniqueId());

        // Teleport player to island spawn if he/she is online
        if (target.isOnline()) {
            islandHandler.teleportToSpawn(target.getPlayer(), islandUuid.toString());
        }

        sender.sendMessage("Island created.");
        return true;
    }
}