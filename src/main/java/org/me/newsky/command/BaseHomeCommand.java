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
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player playerSender = (Player) sender;
        UUID targetUuid = getTargetUUID(sender, args);
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);

        // Check if the target island has an island
        if (islandUuidOpt.isEmpty()) {
            String playerName = Bukkit.getOfflinePlayer(targetUuid).getName();
            sender.sendMessage(playerName + " has no island.");
            return true;
        }

        UUID islandUuid = islandUuidOpt.get();

        CompletableFuture<Void> homeIslandFuture = islandHandler.teleportToIsland(playerSender, islandUuid.toString());
        handleIslandTeleportFuture(homeIslandFuture, sender, islandUuid);

        return true;
    }

    protected void handleIslandTeleportFuture(CompletableFuture<Void> future, CommandSender sender, UUID islandUuid) {
        future.thenRun(() -> {
            sender.sendMessage("Teleported to island:" + islandUuid);
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error teleporting to the island.");
            return null;
        });
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUUID(CommandSender sender, String[] args);
}
