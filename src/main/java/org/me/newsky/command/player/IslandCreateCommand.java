package org.me.newsky.command.player;

import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;

public class IslandCreateCommand implements IslandSubCommand {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final IslandHandler islandHandler;

    public IslandCreateCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
        this.islandHandler = plugin.getIslandHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;

        // Check if player already have an island
        if(cacheHandler.getIslandUuidByPlayerUuid(player.getUniqueId()).isPresent()) {
            sender.sendMessage("You already have an island.");
            return true;
        }

        // Generate island UUID
        UUID islandUuid = UUID.randomUUID();

        // Create island
        islandHandler.createWorld(islandUuid.toString());
        cacheHandler.createIsland(islandUuid, player.getUniqueId());

        // Teleport player to island spawn
        islandHandler.teleportToSpawn(player, islandUuid.toString());

        sender.sendMessage("Island created.");
        return true;
    }
}