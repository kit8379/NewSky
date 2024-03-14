package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class BaseCreateCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;
    protected final IslandHandler islandHandler;

    public BaseCreateCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check if the command arguments are valid
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the target player's UUID
        UUID targetUuid = getTargetUuid(sender, args);

        // Get the target player's island UUID
        Optional<UUID> existingIslandUuid = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (existingIslandUuid.isPresent()) {
            sender.sendMessage(getExistingIslandMessage(args));
            return true;
        }

        // Generate a new island UUID
        UUID islandUuid = UUID.randomUUID();

        // Set the island spawn location
        int spawnX = config.getIslandSpawnX();
        int spawnY = config.getIslandSpawnY();
        int spawnZ = config.getIslandSpawnZ();
        float spawnYaw = config.getIslandSpawnYaw();
        float spawnPitch = config.getIslandSpawnPitch();
        String spawnLocation = spawnX + "," + spawnY + "," + spawnZ + "," + spawnYaw + "," + spawnPitch;

        // Run the island creation future
        CompletableFuture<Void> createIslandFuture = islandHandler.createIsland(islandUuid, targetUuid, spawnLocation);
        handleIslandCreationFuture(createIslandFuture, sender, targetUuid, islandUuid, spawnLocation, args);

        return true;
    }

    protected void handleIslandCreationFuture(CompletableFuture<Void> future, CommandSender sender, UUID targetUuid, UUID islandUuid, String spawnLocation, String[] args) {
        future.thenRun(() -> {
            // Send the success message
            sender.sendMessage(getIslandCreateSuccessMessage(args));
            // Teleport the player to the island
            performPostCreationActions(sender, targetUuid, islandUuid, spawnLocation);
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error creating the island.");
            return null;
        });
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract String getExistingIslandMessage(String[] args);

    protected abstract String getIslandCreateSuccessMessage(String[] args);

    protected abstract void performPostCreationActions(CommandSender sender, UUID targetUuid, UUID islandUuid, String spawnLocation);
}
