package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class BaseWarpCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;
    protected final IslandHandler islandHandler;

    public BaseWarpCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        if (!validateArgs(sender, args)) {
            return true;
        }

        Player playerSender = (Player) sender;
        OfflinePlayer targetWarp = Bukkit.getOfflinePlayer(args[1]);
        Optional<String> warpLocationOpt = cacheHandler.getWarpLocation(targetWarp.getUniqueId());

        // Check if the target island has a warp point set
        if (warpLocationOpt.isEmpty()) {
            sender.sendMessage("§c" + targetWarp.getName() + " does not have a warp point set.");
            return true;
        }

        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetWarp.getUniqueId());

        // Check if the target island has an island
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage("§c" + targetWarp.getName() + " does not have an island.");
            return true;
        }

        // Parse the warp location and teleport the player
        String warpLocation = warpLocationOpt.get();

        // Parse the warp location and teleport the player
        UUID islandUuid = islandUuidOpt.get();

        CompletableFuture<Void> warpIslandFuture = islandHandler.teleportToIsland(islandUuid, playerSender, warpLocation);
        handleIslandTeleportFuture(warpIslandFuture, sender, args);

        return true;
    }

    protected void handleIslandTeleportFuture(CompletableFuture<Void> future, CommandSender sender, String[] args) {
        future.thenRun(() -> {
            sender.sendMessage("Teleported to the island warp point.");
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error teleporting to the island warp point");
            return null;
        });
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
}
