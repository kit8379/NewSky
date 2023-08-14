package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;

public class IslandDeleteCommand implements IslandSubCommand {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final IslandHandler islandHandler;

    public IslandDeleteCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
        this.islandHandler = plugin.getIslandHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;

        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(player.getUniqueId());

        // Check if player have an island
        if (islandUuid.isEmpty()) {
            sender.sendMessage("You don't have an island.");
            return true;
        }

        // Delete island
        islandHandler.deleteWorld(islandUuid.get().toString());
        cacheHandler.deleteIsland(islandUuid.get());
        sender.sendMessage("Island deleted.");

        return true;
    }
}