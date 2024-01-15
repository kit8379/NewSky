package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class BaseCreateCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;
    protected final IslandHandler islandHandler;

    public BaseCreateCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        UUID targetUuid = getTargetUuid(sender, args);

        // Check if player already has an island
        if (cacheHandler.getIslandUuidByPlayerUuid(targetUuid).isPresent()) {
            sender.sendMessage(getExistingIslandMessage(args));
            return true;
        }

        // Generate island UUID
        UUID islandUuid = UUID.randomUUID();

        // Create island
        String spawnLocation = "0,100,0,100,100";
        String role = "owner";
        CompletableFuture<Void> createIslandFuture = islandHandler.createIsland(islandUuid.toString());

        createIslandFuture.thenRun(() -> {
            cacheHandler.createIsland(islandUuid);
            cacheHandler.addIslandPlayer(targetUuid, islandUuid, spawnLocation, role);
            sender.sendMessage(getIslandCreatedMessage(args));
            performPostCreationActions(sender, targetUuid, islandUuid);
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error creating the island: " + ex.getMessage());
            return null;
        });

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);
    protected abstract String getExistingIslandMessage(String[] args);
    protected abstract String getIslandCreatedMessage(String[] args);
    protected abstract void performPostCreationActions(CommandSender sender, UUID targetUuid, UUID islandUuid);
}
