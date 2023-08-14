package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.island.IslandHandler;
import org.me.newsky.redis.RedisHandler;

import java.util.Optional;
import java.util.UUID;

public class AdminDeleteCommand implements IslandSubCommand {
    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final IslandHandler islandHandler;
    private final RedisHandler redisHandler;

    public AdminDeleteCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
        this.islandHandler = plugin.getIslandHandler();
        this.redisHandler = plugin.getRedisHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /islandadmin delete <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(target.getUniqueId());

        if (islandUuid.isPresent()) {
            if (Bukkit.getWorld(islandUuid.get().toString()) != null) {
                islandHandler.deleteWorld(islandUuid.get().toString());
            } else {
                redisHandler.deleteWorldCross(islandUuid.get().toString());
            }

            cacheHandler.deleteIsland(islandUuid.get());
            sender.sendMessage("Deleted " + target.getName() + " island.");
        } else {
            sender.sendMessage(target.getName() + " don't have an island.");
        }

        return true;
    }
}
