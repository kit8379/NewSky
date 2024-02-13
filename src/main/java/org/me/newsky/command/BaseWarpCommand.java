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
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        if (!validateArgs(sender, args)) {
            return true;
        }

        Player player = (Player) sender;
        UUID targetUuid = getTargetUUID(sender, args);

        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage("§c" + args[1] + " does not have an island.");
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        boolean isLocked = cacheHandler.getIslandLock(islandUuid);
        if (isLocked && !cacheHandler.getIslandMembers(islandUuid).contains(targetUuid)) {
            sender.sendMessage("§cThe island is currently locked.");
            return true;
        }

        Set<String> warpNames = cacheHandler.getWarpNames(islandUuid, targetUuid);
        if (warpNames.isEmpty()) {
            sender.sendMessage("§c" + args[1] + " does not have any warp points set.");
            return true;
        }

        String warpName = args.length > getTargetWarpArgIndex() ? args[getTargetWarpArgIndex()] : "default";
        Optional<String> warpLocationOpt = cacheHandler.getWarpLocation(islandUuid, targetUuid, warpName);
        if (warpLocationOpt.isEmpty()) {
            sender.sendMessage(getNoWarpMessage(args, warpName));
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
            return warpNames.stream()
                    .filter(name -> name.toLowerCase().startsWith(args[getTargetWarpArgIndex()].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null;
    }

    protected void handleIslandTeleportFuture(CompletableFuture<Void> future, CommandSender sender, String warpName) {
        future.thenRun(() -> sender.sendMessage("Teleported to the warp point '" + warpName + "'."))
                .exceptionally(ex -> {
                    sender.sendMessage("There was an error teleporting to the warp point");
                    return null;
                });
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUUID(CommandSender sender, String[] args);

    protected abstract int getTargetWarpArgIndex();

    protected abstract String getNoWarpMessage(String[] args, String warpName);
}
