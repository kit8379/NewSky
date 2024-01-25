package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
        UUID targetUuid = getTargetUUID(sender, args);

        // Check if the player has an island
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage("§c" + args[1] + " does not have an island.");
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Check if the island is locked
        boolean isLocked = cacheHandler.getIslandLock(islandUuid);
        if (isLocked) {
            if (!cacheHandler.getIslandMembers(islandUuid).contains(targetUuid)) {
                sender.sendMessage("§cThe island is currently locked.");
                return true;
            }
        }

        // Check if the player has warp points
        Set<String> warpNames = cacheHandler.getWarpNames(targetUuid);
        if (warpNames.isEmpty()) {
            sender.sendMessage("§c" + args[1] + " does not have any warp points set.");
            return true;
        }

        // Get the warp name from the command arguments or a random warp name
        String warpName = args.length > 2 ? args[2] : "default";
        args[2] = warpName;

        // Check if the target warp point exists
        Optional<String> warpLocationOpt = cacheHandler.getWarpLocation(targetUuid, warpName);
        if (warpLocationOpt.isEmpty()) {
            sender.sendMessage(getNoWarpMessage(args));
            return true;
        }
        String warpLocation = warpLocationOpt.get();

        // Run the island teleport future
        CompletableFuture<Void> warpIslandFuture = islandHandler.teleportToIsland(islandUuid, player, warpLocation);
        handleIslandTeleportFuture(warpIslandFuture, sender, warpName);

        return true;
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, String[] args) {
        if (args.length == getTargetWarpArgIndex() + 1) {
            UUID targetUuid = getTargetUUID(sender, args);
            Set<String> homeNames = cacheHandler.getWarpNames(targetUuid);
            return homeNames.stream().filter(name -> name.toLowerCase().startsWith(args[getTargetWarpArgIndex()].toLowerCase())).collect(Collectors.toList());
        }
        return null;
    }

    protected void handleIslandTeleportFuture(CompletableFuture<Void> future, CommandSender sender, String warpName) {
        future.thenRun(() -> {
            // Send the success message
            sender.sendMessage("Teleported to the warp point '" + warpName + "'.");
        }).exceptionally(ex -> {
            // Send the error message
            sender.sendMessage("There was an error teleporting to the warp point");
            return null;
        });
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUUID(CommandSender sender, String[] args);

    protected abstract int getTargetWarpArgIndex();

    protected abstract String getNoWarpMessage(String[] args);
}
