package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.UpgradeDoesNotExistException;
import org.me.newsky.exceptions.UpgradeIslandLevelTooLowException;
import org.me.newsky.exceptions.UpgradeMaxedException;
import org.me.newsky.island.UpgradeHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /is upgrade
 * /is upgrade <upgradeId>
 * /is upgrade <upgradeId> buy
 */
public class PlayerUpgradeCommand implements SubCommand, AsyncTabComplete {

    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerUpgradeCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "upgrade";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerUpgradeAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerUpgradePermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerUpgradeSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerUpgradeDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        if (args.length < 2) {
            return false;
        }

        UUID playerUuid = player.getUniqueId();
        String upgradeId = args[1].toLowerCase(Locale.ROOT);

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> {
            // /is upgrade <upgradeId>
            // Show specific upgrade details
            if (args.length == 2) {
                if (!api.getUpgradeIds().contains(upgradeId)) {
                    player.sendMessage(config.getPlayerUpgradeInvalidIdMessage(upgradeId));
                    return CompletableFuture.completedFuture(null);
                }

                return api.getCurrentUpgradeLevel(islandUuid, upgradeId).thenCompose(currentLevel -> {
                    String currentValue = formatUpgradeValue(upgradeId, currentLevel);

                    int nextLevel = api.getNextUpgradeLevel(upgradeId, currentLevel);
                    boolean maxed = nextLevel == -1;

                    player.sendMessage(config.getPlayerUpgradeDetailsHeaderMessage(upgradeId));
                    player.sendMessage(config.getPlayerUpgradeDetailsCurrentLevelMessage(currentLevel));
                    player.sendMessage(config.getPlayerUpgradeDetailsCurrentValueMessage(currentValue));

                    if (maxed) {
                        return CompletableFuture.completedFuture(null);
                    }

                    String nextValue = formatUpgradeValue(upgradeId, nextLevel);
                    int requireIslandLevel = api.getUpgradeRequireIslandLevel(upgradeId, nextLevel);

                    return api.getIslandLevel(islandUuid).thenAccept(islandLevel -> {
                        player.sendMessage(config.getPlayerUpgradeDetailsNextLevelMessage(String.valueOf(nextLevel)));
                        player.sendMessage(config.getPlayerUpgradeDetailsNextValueMessage(nextValue));
                        player.sendMessage(config.getPlayerUpgradeDetailsRequireIslandLevelMessage(requireIslandLevel));
                        player.sendMessage(config.getPlayerUpgradeDetailsYourIslandLevelMessage(islandLevel));

                        if (islandLevel < requireIslandLevel) {
                            player.sendMessage(config.getPlayerUpgradeDetailsStatusLockedMessage());
                        } else {
                            player.sendMessage(config.getPlayerUpgradeDetailsStatusAvailableMessage());
                        }
                    });
                });
            }

            // /is upgrade <upgradeId> buy
            if (args.length != 3 || !args[2].equalsIgnoreCase("buy")) {
                return CompletableFuture.completedFuture(null);
            }

            return api.upgradeToNextLevel(islandUuid, upgradeId).thenAccept(result -> {
                player.sendMessage(config.getPlayerUpgradeBuySuccessMessage(result.getUpgradeId(), result.getOldLevel(), result.getNewLevel(), result.getRequireIslandLevel()));
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (cause instanceof UpgradeDoesNotExistException) {
                player.sendMessage(config.getPlayerUpgradeInvalidIdMessage(upgradeId));
            } else if (cause instanceof UpgradeMaxedException) {
                player.sendMessage(config.getPlayerUpgradeMaxedMessage(upgradeId));
            } else if (cause instanceof UpgradeIslandLevelTooLowException) {
                player.sendMessage(config.getPlayerUpgradeIslandLevelTooLowMessage(upgradeId));
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error handling upgrade command for player " + player.getName() + " upgradeId=" + upgradeId, ex);
            }
            return null;
        });

        return true;
    }

    private String formatUpgradeValue(String upgradeId, int level) {
        if (UpgradeHandler.UPGRADE_TEAM_LIMIT.equals(upgradeId)) {
            return String.valueOf(api.getTeamLimit(level));
        }

        if (UpgradeHandler.UPGRADE_WARP_LIMIT.equals(upgradeId)) {
            return String.valueOf(api.getWarpLimit(level));
        }

        if (UpgradeHandler.UPGRADE_HOME_LIMIT.equals(upgradeId)) {
            return String.valueOf(api.getHomeLimit(level));
        }

        if (UpgradeHandler.UPGRADE_COOP_LIMIT.equals(upgradeId)) {
            return String.valueOf(api.getCoopLimit(level));
        }

        if (UpgradeHandler.UPGRADE_ISLAND_SIZE.equals(upgradeId)) {
            return String.valueOf(api.getIslandSize(level));
        }

        if (UpgradeHandler.UPGRADE_GENERATOR_RATES.equals(upgradeId)) {
            Map<String, Double> rates = api.getGeneratorRates(level);
            return formatRates(rates);
        }

        if (UpgradeHandler.UPGRADE_BIOMES.equals(upgradeId)) {
            Set<String> biomes = api.getBiomeAllowList(level);
            return formatBiomes(biomes);
        }

        return config.getUpgradeUnknownValue();
    }

    private String formatRates(Map<String, Double> rates) {
        if (rates == null || rates.isEmpty()) {
            return config.getUpgradeUnknownValue();
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, Double> e : rates.entrySet()) {
            String key = e.getKey();
            Double val = e.getValue();
            if (key == null || val == null) {
                continue;
            }

            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(key).append(": ").append(val);
        }

        return sb.toString();
    }

    private String formatBiomes(Set<String> biomes) {
        if (biomes == null || biomes.isEmpty()) {
            return config.getUpgradeUnknownValue();
        }

        return biomes.stream().filter(Objects::nonNull).filter(name -> !name.isBlank()).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", "));
    }

    @Override
    public CompletableFuture<List<String>> tabCompleteAsync(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        // /is upgrade <upgradeId>
        if (args.length == 2) {
            Set<String> ids = api.getUpgradeIds();
            String prefix = args[1].toLowerCase(Locale.ROOT);

            return CompletableFuture.completedFuture(ids.stream().filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList()));
        }

        // /is upgrade <upgradeId> buy
        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            if ("buy".startsWith(prefix)) {
                return CompletableFuture.completedFuture(Collections.singletonList("buy"));
            }
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}