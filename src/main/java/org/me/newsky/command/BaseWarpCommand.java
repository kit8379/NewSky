package org.me.newsky.command;

import org.bukkit.Bukkit;
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
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        if (!validateArgs(sender, args)) {
            return true;
        }

        Player player = (Player) sender;
        UUID targetUuid = getTargetUUID(sender, args);

        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(config.getNoIslandMessage(args[1]));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        boolean isLocked = cacheHandler.getIslandLock(islandUuid);
        if (isLocked && !cacheHandler.getIslandMembers(islandUuid).contains(targetUuid)) {
            sender.sendMessage(config.getIslandLockedMessage());
            sender.sendMessage();
            return true;
        }

        String warpName = args.length > getTargetWarpArgIndex() ? args[getTargetWarpArgIndex()] : "default";
        Optional<String> warpLocationOpt = cacheHandler.getWarpLocation(islandUuid, targetUuid, warpName);
        if (warpLocationOpt.isEmpty()) {
            sender.sendMessage(config.getNoWarpMessage(args[1], warpName));
            return true;
        }
        String warpLocation = warpLocationOpt.get();

        CompletableFuture<Void> warpIslandFuture = islandHandler.teleportToIsland(islandUuid, player, warpLocation);
        handleIslandTeleportFuture(warpIslandFuture, sender, warpName);

        return true;
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == getTargetWarpArgIndex() + 1) {
            UUID targetUuid = getTargetUUID(sender, args);
            Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
            if (islandUuidOpt.isEmpty()) {
                return null;
            }
            UUID islandUuid = islandUuidOpt.get();
            Set<String> warpNames = cacheHandler.getWarpNames(islandUuid, targetUuid);
            return warpNames.stream().filter(name -> name.toLowerCase().startsWith(args[getTargetWarpArgIndex()].toLowerCase())).collect(Collectors.toList());
        }
        return null;
    }

    protected void handleIslandTeleportFuture(CompletableFuture<Void> future, CommandSender sender, String warpName) {
        future.thenRun(() -> {
            // Send the success message
            sender.sendMessage(config.getWarpSuccessMessage(warpName));
        }).exceptionally(ex -> {
            // Send the error message
            if (ex instanceof IllegalStateException) {
                sender.sendMessage(ex.getMessage());
            } else {
                ex.printStackTrace();
                sender.sendMessage("There was an error creating the island.");
            }
            return null;
        });
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUUID(CommandSender sender, String[] args);

    protected abstract int getTargetWarpArgIndex();

}
