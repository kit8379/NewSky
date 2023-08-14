package org.me.newsky.command.player;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.island.IslandHandler;
import org.me.newsky.redis.RedisHandler;

import java.util.Optional;
import java.util.UUID;

public class IslandDeleteCommand implements IslandSubCommand {
    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final IslandHandler islandHandler;
    private final RedisHandler redisHandler;

    public IslandDeleteCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
        this.islandHandler = plugin.getIslandHandler();
        this.redisHandler = plugin.getRedisHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /island delete");
            return true;
        }

        Player player = (Player) sender;
        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(player.getUniqueId());

        if (islandUuid.isPresent()) {
            if (Bukkit.getWorld(islandUuid.get().toString()) != null) {
                islandHandler.deleteWorld(islandUuid.get().toString());
            } else {
                redisHandler.deleteWorldCross(islandUuid.get().toString());
            }

            cacheHandler.deleteIsland(islandUuid.get());
            sender.sendMessage("Deleted island.");
        } else {
            sender.sendMessage("You don't have an island.");
        }

        return true;
    }
}
