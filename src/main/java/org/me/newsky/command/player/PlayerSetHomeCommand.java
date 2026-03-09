package org.me.newsky.command.player;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.HomeNameNotLegalException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;
import org.me.newsky.island.UpgradeHandler;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /is sethome [homeName]
 */
public class PlayerSetHomeCommand implements SubCommand, AsyncTabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerSetHomeCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "sethome";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerSetHomeAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerSetHomePermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerSetHomeSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerSetHomeDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        String homeName = (args.length >= 2) ? args[1] : "default";
        UUID playerUuid = player.getUniqueId();

        Location loc = player.getLocation();
        String worldName = loc.getWorld().getName();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.getHomeNames(playerUuid).thenCompose(existingHomes -> {
            boolean overwriting = existingHomes.stream().anyMatch(n -> n.equalsIgnoreCase(homeName));

            return api.getCurrentUpgradeLevel(islandUuid, UpgradeHandler.UPGRADE_HOME_LIMIT).thenCompose(homeLimitLevel -> {
                int homeLimit = api.getHomeLimit(homeLimitLevel);

                if (!overwriting && existingHomes.size() >= homeLimit) {
                    player.sendMessage(config.getPlayerHomeLimitReachedMessage(homeLimit));
                    return CompletableFuture.completedFuture(null);
                }

                return api.setHome(playerUuid, homeName, worldName, x, y, z, yaw, pitch).thenRun(() -> {
                    player.sendMessage(config.getPlayerSetHomeSuccessMessage(homeName));
                });
            });
        })).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (cause instanceof LocationNotInIslandException) {
                player.sendMessage(config.getPlayerMustInIslandSetHomeMessage());
            } else if (cause instanceof HomeNameNotLegalException) {
                player.sendMessage(config.getHomeNameNotLegalMessage());
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error setting home for player " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public CompletableFuture<List<String>> tabCompleteAsync(CommandSender sender, String label, String[] args) {
        if (args.length != 2 || !(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String prefix = args[1].toLowerCase(Locale.ROOT);

        return api.getHomeNames(player.getUniqueId()).thenApply(homes -> homes.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList())).exceptionally(ex -> Collections.emptyList());
    }
}