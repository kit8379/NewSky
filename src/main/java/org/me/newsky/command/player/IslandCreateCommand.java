package org.me.newsky.command.player;

import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.me.newsky.command.BaseCreateCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandCreateCommand extends BaseCreateCommand {

    public IslandCreateCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(config, cacheHandler, islandHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected String getExistingIslandMessage(String[] args) {
        return config.getHasIslandMessage();
    }

    @Override
    protected String getIslandCreatedMessage(String[] args) {
        return config.getIslandCreatedMessage();
    }

    @Override
    protected void performPostCreationActions(CommandSender sender, UUID targetUuid, UUID islandUuid) {
        // Teleport player to island
        CompletableFuture<Void> homeIslandFuture = islandHandler.teleportToIsland((Player) sender, islandUuid.toString());
        homeIslandFuture.thenRun(() -> {
            sender.sendMessage("Teleported to island");
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error teleporting to the island: " + ex.getMessage());
            return null;
        });

        sender.sendMessage("Teleporting to Island...");
    }
}
