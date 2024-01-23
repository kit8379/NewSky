package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.Random;
import java.util.Set;
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
        // Check if the sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        // Check if the command arguments are valid
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Cast the sender to a player
        Player player = (Player) sender;

        // Get the target player's UUID
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[0]);

        // Get the target warp name
        UUID targetUuid = targetPlayer.getUniqueId();

        // Check if the player has warp points
        Set<String> warpNames = cacheHandler.getWarpNames(targetUuid);
        if (warpNames.isEmpty()) {
            sender.sendMessage("§c" + targetPlayer.getName() + " does not have any warp points set.");
            return true;
        }

        // Get the warp name from the command arguments or a random warp name
        String warpName = args.length > 1 ? args[1] : getRandomWarpName(warpNames);

        // Check if the target warp point exists
        Optional<String> warpLocationOpt = cacheHandler.getWarpLocation(targetUuid, warpName);
        if (warpLocationOpt.isEmpty()) {
            sender.sendMessage("§cWarp point '" + warpName + "' not found for " + targetPlayer.getName() + ".");
            return true;
        }
        String warpLocation = warpLocationOpt.get();

        // Check if the player has an island
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage("§c" + targetPlayer.getName() + " does not have an island.");
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Run the island teleport future
        CompletableFuture<Void> warpIslandFuture = islandHandler.teleportToIsland(islandUuid, player, warpLocation);
        handleIslandTeleportFuture(warpIslandFuture, sender, warpName);

        return true;
    }

    private String getRandomWarpName(Set<String> warpNames) {
        int index = new Random().nextInt(warpNames.size());
        return (String) warpNames.toArray()[index];
    }

    protected void handleIslandTeleportFuture(CompletableFuture<Void> future, CommandSender sender, String warpName) {
        future.thenRun(() -> {
                    // Send the success message
                    sender.sendMessage("Teleported to the warp point '" + warpName + "'.");
                })
                .exceptionally(ex -> {
                    // Send the error message
                    sender.sendMessage("There was an error teleporting to the warp point");
                    return null;
                });
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
}
