// PlayerUpgradeCommand.java
package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.UpgradeDoesNotExistException;
import org.me.newsky.exceptions.UpgradeIslandLevelTooLowException;
import org.me.newsky.exceptions.UpgradeMaxedException;
import org.me.newsky.island.UpgradeHandler;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /is upgrade
 * /is upgrade <upgradeId>
 * /is upgrade <upgradeId> buy
 */
public class PlayerUpgradeCommand implements SubCommand, TabComplete {

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

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(playerUuid);
        } catch (IslandDoesNotExistException e) {
            player.sendMessage(config.getPlayerNoIslandMessage());
            return true;
        }

        String upgradeId = args[1].toLowerCase();

        // /is upgrade <upgradeId>
        // Show specific upgrade details
        if (args.length == 2) {
            try {
                if (!api.getUpgradeIds().contains(upgradeId)) {
                    player.sendMessage(config.getPlayerUpgradeInvalidIdMessage(upgradeId));
                    return true;
                }

                int currentLevel = api.getCurrentUpgradeLevel(islandUuid, upgradeId);

                String currentValue = formatUpgradeValue(upgradeId, currentLevel);

                int nextLevel = api.getNextUpgradeLevel(upgradeId, currentLevel);
                boolean maxed = (nextLevel == -1);

                player.sendMessage(config.getPlayerUpgradeDetailsHeaderMessage(upgradeId));
                player.sendMessage(config.getPlayerUpgradeDetailsCurrentLevelMessage(currentLevel));
                player.sendMessage(config.getPlayerUpgradeDetailsCurrentValueMessage(currentValue));

                if (maxed) {
                    return true;
                }

                String nextValue = formatUpgradeValue(upgradeId, nextLevel);

                int requireIslandLevel = api.getUpgradeRequireIslandLevel(upgradeId, nextLevel);
                int islandLevel = api.getIslandLevel(islandUuid);

                player.sendMessage(config.getPlayerUpgradeDetailsNextLevelMessage(String.valueOf(nextLevel)));
                player.sendMessage(config.getPlayerUpgradeDetailsNextValueMessage(nextValue));
                player.sendMessage(config.getPlayerUpgradeDetailsRequireIslandLevelMessage(requireIslandLevel));
                player.sendMessage(config.getPlayerUpgradeDetailsYourIslandLevelMessage(islandLevel));

                if (islandLevel < requireIslandLevel) {
                    player.sendMessage(config.getPlayerUpgradeDetailsStatusLockedMessage());
                } else {
                    player.sendMessage(config.getPlayerUpgradeDetailsStatusAvailableMessage());
                }

            } catch (Exception e) {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error showing specific upgrade details for player " + player.getName() + " upgradeId=" + upgradeId, e);
            }

            return true;
        }

        // /is upgrade <upgradeId> buy
        if (!args[2].equalsIgnoreCase("buy")) {
            return false;
        }

        api.upgradeToNextLevel(islandUuid, upgradeId).thenAccept(result -> {
            if (args[1].equals(UpgradeHandler.UPGRADE_ISLAND_SIZE)) {
                api.applyBorder(islandUuid);
            }
            player.sendMessage(config.getPlayerUpgradeBuySuccessMessage(result.getUpgradeId(), result.getOldLevel(), result.getNewLevel(), result.getRequireIslandLevel()));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof UpgradeDoesNotExistException) {
                player.sendMessage(config.getPlayerUpgradeInvalidIdMessage(upgradeId));
            } else if (cause instanceof UpgradeMaxedException) {
                player.sendMessage(config.getPlayerUpgradeMaxedMessage(upgradeId));
            } else if (cause instanceof UpgradeIslandLevelTooLowException) {
                player.sendMessage(config.getPlayerUpgradeIslandLevelTooLowMessage(upgradeId));
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error buying upgrade for player " + player.getName() + " upgradeId=" + upgradeId, ex);
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

        return "N/A";
    }

    private String formatRates(Map<String, Double> rates) {
        if (rates == null || rates.isEmpty()) {
            return "N/A";
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, Double> e : rates.entrySet()) {
            String key = e.getKey();
            Double val = e.getValue();
            if (key == null || val == null) continue;

            if (!first) sb.append(", ");
            first = false;
            sb.append(key).append(": ").append(val);
        }

        return sb.toString();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        // /is upgrade <upgradeId>
        if (args.length == 2) {
            Set<String> ids;
            try {
                ids = api.getUpgradeIds();
            } catch (Exception e) {
                return Collections.emptyList();
            }

            String prefix = args[1].toLowerCase();
            return ids.stream().filter(id -> id.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }

        // /is upgrade <upgradeId> buy
        if (args.length == 3) {
            String prefix = args[2].toLowerCase();
            if ("buy".startsWith(prefix)) {
                return Collections.singletonList("buy");
            }
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }
}