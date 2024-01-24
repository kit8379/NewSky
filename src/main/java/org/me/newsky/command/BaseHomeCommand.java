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
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!validateArgs(sender, args)) {
            return true;
        }

        // Cast the sender to a player
        Player player = (Player) sender;

        // Get the target player's UUID
        UUID targetUuid = getTargetUUID(sender, args);

        // Get the target home name
        String homeName = args[getTargetHomeArgIndex()];

        // Get the target player's island UUID
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Get the target player's home location
        Optional<String> homeLocationOpt = cacheHandler.getHomeLocation(targetUuid, homeName);
        if (homeLocationOpt.isEmpty()) {
            sender.sendMessage(getNoHomeMessage(args));
            return true;
        }
        String homeLocation = homeLocationOpt.get();

        CompletableFuture<Void> homeIslandFuture = islandHandler.teleportToIsland(islandUuid, player, homeLocation);

        handleIslandTeleportFuture(homeIslandFuture, sender, args);

        return true;
    }

    protected void handleIslandTeleportFuture(CompletableFuture<Void> future, CommandSender sender, String[] args) {
        future.thenRun(() -> sender.sendMessage(getIslandHomeSuccessMessage(args)))
                .exceptionally(ex -> {
                    sender.sendMessage("There was an error teleporting to the island.");
                    return null;
                });
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUUID(CommandSender sender, String[] args);

    protected abstract int getTargetHomeArgIndex();

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getNoHomeMessage(String[] args);

    protected abstract String getIslandHomeSuccessMessage(String[] args);
}
