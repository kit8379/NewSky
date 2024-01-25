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

        Player player = (Player) sender;
        UUID targetUuid = getTargetUUID(sender, args);

        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        Set<String> homeNames = cacheHandler.getHomeNames(targetUuid);
        if (homeNames.isEmpty()) {
            sender.sendMessage(getNoHomesMessage(args));
            return true;
        }

        String homeName = args.length > getTargetHomeArgIndex() ? args[getTargetHomeArgIndex()] : "default";
        Optional<String> homeLocationOpt = cacheHandler.getHomeLocation(targetUuid, homeName);
        if (homeLocationOpt.isEmpty()) {
            sender.sendMessage(getNoHomeMessage(args, homeName));
            return true;
        }
        String homeLocation = homeLocationOpt.get();

        CompletableFuture<Void> homeIslandFuture = islandHandler.teleportToIsland(islandUuid, player, homeLocation);
        handleIslandTeleportFuture(homeIslandFuture, sender, homeName);

        return true;
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, String[] args) {
        if (args.length == getTargetHomeArgIndex() + 1) {
            UUID targetUuid = getTargetUUID(sender, args);
            Set<String> homeNames = cacheHandler.getHomeNames(targetUuid);
            return homeNames.stream()
                    .filter(name -> name.toLowerCase().startsWith(args[getTargetHomeArgIndex()].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null;
    }

    protected void handleIslandTeleportFuture(CompletableFuture<Void> future, CommandSender sender, String homeName) {
        future.thenRun(() -> {
            // Send the success message
            sender.sendMessage(getIslandHomeSuccessMessage(homeName));
        }).exceptionally(ex -> {
            // Send the error message
            sender.sendMessage("There was an error teleporting to the island home.");
            return null;
        });
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUUID(CommandSender sender, String[] args);

    protected abstract int getTargetHomeArgIndex();

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getNoHomesMessage(String[] args);

    protected abstract String getNoHomeMessage(String[] args, String homeName);

    protected abstract String getIslandHomeSuccessMessage(String homeName);
}
