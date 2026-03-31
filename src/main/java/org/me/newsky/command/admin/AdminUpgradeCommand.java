package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.UpgradeDoesNotExistException;
import org.me.newsky.exceptions.UpgradeLevelDoesNotExistException;
import org.me.newsky.island.UpgradeHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /isadmin upgrade <player> <upgradeId>
 * /isadmin upgrade <player> <upgradeId> set <level>
 */
public class AdminUpgradeCommand implements SubCommand, AsyncTabComplete {

    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminUpgradeCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
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
        return config.getAdminUpgradeAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminUpgradePermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminUpgradeSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminUpgradeDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 3 && args.length != 5) {
            return false;
        }

        String targetPlayerName = args[1];

        api.getPlayerUuid(targetPlayerName).thenCompose(targetUuidOpt -> {
            if (targetUuidOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(targetPlayerName));
                return CompletableFuture.completedFuture(null);
            }

            UUID targetPlayerUuid = targetUuidOpt.get();

            return api.getIslandUuid(targetPlayerUuid).thenCompose(islandUuid -> {
                // /isadmin upgrade <player> <upgradeId>
                if (args.length == 3) {
                    String upgradeId = args[2].toLowerCase(Locale.ROOT);

                    if (!api.getUpgradeIds().contains(upgradeId)) {
                        sender.sendMessage(config.getPlayerUpgradeInvalidIdMessage(upgradeId));
                        return CompletableFuture.completedFuture(null);
                    }

                    return api.getCurrentUpgradeLevel(islandUuid, upgradeId).thenAccept(currentLevel -> {
                        String currentValue = formatUpgradeValue(upgradeId, currentLevel);

                        sender.sendMessage(config.getAdminUpgradeDetailsHeaderMessage(targetPlayerName, upgradeId));
                        sender.sendMessage(config.getAdminUpgradeDetailsCurrentLevelMessage(upgradeId, currentLevel));
                        sender.sendMessage(config.getAdminUpgradeDetailsCurrentValueMessage(currentValue));
                    });
                }

                // /isadmin upgrade <player> <upgradeId> set <level>
                if (!args[3].equalsIgnoreCase("set")) {
                    return CompletableFuture.completedFuture(null);
                }

                String upgradeId = args[2].toLowerCase(Locale.ROOT);
                int level;

                try {
                    level = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(config.getAdminUpgradeInvalidLevelMessage());
                    return CompletableFuture.completedFuture(null);
                }

                return api.setUpgradeLevel(islandUuid, upgradeId, level).thenRun(() -> sender.sendMessage(config.getAdminUpgradeSetSuccessMessage(upgradeId, level)));
            });
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(targetPlayerName));
            } else if (cause instanceof UpgradeDoesNotExistException) {
                String upgradeId = args[2].toLowerCase(Locale.ROOT);
                sender.sendMessage(config.getPlayerUpgradeInvalidIdMessage(upgradeId));
            } else if (cause instanceof UpgradeLevelDoesNotExistException) {
                sender.sendMessage(config.getAdminUpgradeInvalidLevelMessage());
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());

                String upgradeId = args[2].toLowerCase(Locale.ROOT);
                if (args.length == 3) {
                    plugin.severe("Error showing admin upgrade details target=" + targetPlayerName + " upgradeId=" + upgradeId, ex);
                } else {
                    String levelText = args[4];
                    plugin.severe("Error setting upgrade level target=" + targetPlayerName + " upgradeId=" + upgradeId + " level=" + levelText, ex);
                }
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

        return "N/A";
    }

    private String formatRates(Map<String, Double> rates) {
        if (rates == null || rates.isEmpty()) {
            return "N/A";
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            String key = entry.getKey();
            Double value = entry.getValue();

            if (key == null || value == null) {
                continue;
            }

            if (!first) {
                sb.append(", ");
            }

            first = false;
            sb.append(key).append(": ").append(value);
        }

        return sb.toString();
    }

    private String formatBiomes(Set<String> biomes) {
        if (biomes == null || biomes.isEmpty()) {
            return "N/A";
        }

        return biomes.stream().filter(Objects::nonNull).filter(name -> !name.isBlank()).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", "));
    }

    @Override
    public CompletableFuture<List<String>> tabCompleteAsync(CommandSender sender, String label, String[] args) {
        // /isadmin upgrade <player>
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return api.getOnlinePlayersNames().thenApply(names -> names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList())).exceptionally(ex -> Collections.emptyList());
        }

        // /isadmin upgrade <player> <upgradeId>
        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            Set<String> ids = api.getUpgradeIds();

            List<String> result = ids.stream().filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());

            return CompletableFuture.completedFuture(result);
        }

        // /isadmin upgrade <player> <upgradeId> set
        if (args.length == 4) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            if ("set".startsWith(prefix)) {
                return CompletableFuture.completedFuture(Collections.singletonList("set"));
            }
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}