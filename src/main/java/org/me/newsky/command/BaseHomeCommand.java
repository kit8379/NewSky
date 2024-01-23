package org.me.newsky.command;

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
        // Check if the sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        // Check if the command arguments are valid
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Cast the sender to a player
        Player playerSender = (Player) sender;

        // Get the target player's UUID
        UUID targetUuid = getTargetUUID(sender, args);

        // Check if the player has an island
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Get the target player's island spawn location
        Optional<String> spawnLocationOpt = cacheHandler.getPlayerIslandSpawn(targetUuid, islandUuid);
        if (spawnLocationOpt.isEmpty()) {
            sender.sendMessage("The target island does not have a spawn.");
            return true;
        }
        String spawnLocation = spawnLocationOpt.get();

        // Run the island teleportation future
        CompletableFuture<Void> homeIslandFuture = islandHandler.teleportToIsland(islandUuid, playerSender, spawnLocation);
        handleIslandTeleportFuture(homeIslandFuture, sender, args);

        return true;
    }

    protected void handleIslandTeleportFuture(CompletableFuture<Void> future, CommandSender sender, String[] args) {
        future.thenRun(() -> {
            // Send the success message
            sender.sendMessage(getIslandHomeSuccessMessage(args));
        }).exceptionally(ex -> {
            // Send the error message
            sender.sendMessage("There was an error teleporting to the island.");
            return null;
        });
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUUID(CommandSender sender, String[] args);

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getIslandHomeSuccessMessage(String[] args);
}
