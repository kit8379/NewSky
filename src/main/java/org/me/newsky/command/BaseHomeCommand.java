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

public abstract class BaseHomeCommand implements BaseCommand {

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
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        // Validate the command arguments
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the target UUID
        UUID targetUuid = getTargetUuid(sender, args);

        // Get the island UUID
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Teleport the player to the island
        String homeName = args.length > getTargetHomeArgIndex() ? args[getTargetHomeArgIndex()] : "default";
        Optional<String> homeLocationOpt = cacheHandler.getHomeLocation(islandUuid, targetUuid, homeName);
        if (homeLocationOpt.isEmpty()) {
            sender.sendMessage(getNoHomeMessage(args, homeName));
            return true;
        }
        String homeLocation = homeLocationOpt.get();

        // Teleport the player to the island
        CompletableFuture<Void> homeIslandFuture = islandHandler.teleportToIsland(islandUuid, (Player) sender, homeLocation);
        handleIslandTeleportFuture(homeIslandFuture, sender, homeName);

        return true;
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == getTargetHomeArgIndex() + 1) {
            UUID targetUuid = getTargetUuid(sender, args);
            Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
            if (islandUuidOpt.isEmpty()) {
                return null;
            }
            UUID islandUuid = islandUuidOpt.get();
            Set<String> homeNames = cacheHandler.getHomeNames(islandUuid, targetUuid);
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

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract int getTargetHomeArgIndex();

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getNoHomeMessage(String[] args, String homeName);

    protected abstract String getIslandHomeSuccessMessage(String homeName);
}
