package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class BaseHomeCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;
    protected final IslandHandler islandHandler;

    public BaseHomeCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Check if the sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getNoConsoleMessage());
            return true;
        }

        UUID targetUuid = getTargetUUID(sender, args);
        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);

        // Check if the target island owner has an island
        if (islandUuid.isEmpty()) {
            sender.sendMessage(config.getPlayerNoIslandMessage(Bukkit.getOfflinePlayer(targetUuid).getName()));
            return true;
        }

        // Teleport player to island
        CompletableFuture<Void> homeIslandFuture = islandHandler.teleportToIsland((Player) sender, islandUuid.get().toString());
        homeIslandFuture.thenRun(() -> {
            sender.sendMessage("Teleported to island:" + islandUuid.get());
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error teleporting to the island: " + ex.getMessage());
            return null;
        });

        return true;
    }


    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract UUID getTargetUUID(CommandSender sender, String[] args);
}
