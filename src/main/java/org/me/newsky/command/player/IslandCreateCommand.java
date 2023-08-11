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
        if (args.length != 1) {
            sender.sendMessage("Usage: /island create");
            return true;
        }

        Player player = (Player) sender;

        UUID islandUuid = UUID.randomUUID();
        cacheHandler.createIsland(islandUuid, player.getUniqueId());
        islandHandler.createWorld(islandUuid.toString());
        islandHandler.teleportToSpawn(player, islandUuid.toString());
        sender.sendMessage("Island created.");
        return true;
    }
}
