// AdminUpgradeCommand.java
package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.UpgradeDoesNotExistException;
import org.me.newsky.exceptions.UpgradeLevelDoesNotExistException;
import org.me.newsky.island.UpgradeHandler;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /isadmin upgrade
 * /isadmin upgrade <player> <upgradeId>
 * /isadmin upgrade <player> <upgradeId> set <level>
 */
public class AdminUpgradeCommand implements SubCommand, TabComplete {

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
        if (args.length < 3 || args.length == 4) {
            return false;
        }

        String targetPlayerName = args[1];

        Optional<UUID> targetUuidOpt = api.getPlayerUuid(targetPlayerName);
        if (targetUuidOpt.isEmpty()) {
            sender.sendMessage(config.getUnknownPlayerMessage(targetPlayerName));
            return true;
        }

        UUID targetPlayerUuid = targetUuidOpt.get();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(targetPlayerUuid);
        } catch (IslandDoesNotExistException e) {
            sender.sendMessage(config.getAdminNoIslandMessage(targetPlayerName));
            return true;
        } catch (Exception e) {
            sender.sendMessage(config.getUnknownExceptionMessage());
            plugin.severe("Error resolving islandUuid for admin upgrade target=" + targetPlayerName, e);
            return true;
        }

        // /isadmin upgrade <player> <upgradeId>
        // Show specific upgrade details
        if (args.length == 3) {
            String upgradeId = args[2].toLowerCase(Locale.ROOT);

            try {
                if (!api.getUpgradeIds().contains(upgradeId)) {
                    sender.sendMessage(config.getPlayerUpgradeInvalidIdMessage(upgradeId));
                    return true;
                }

                int currentLevel = api.getCurrentUpgradeLevel(islandUuid, upgradeId);

                String currentValue = formatUpgradeValue(upgradeId, currentLevel);

                sender.sendMessage(config.getAdminUpgradeDetailsHeaderMessage(targetPlayerName, upgradeId));
                sender.sendMessage(config.getAdminUpgradeDetailsCurrentLevelMessage(upgradeId, currentLevel));
                sender.sendMessage(config.getAdminUpgradeDetailsCurrentValueMessage(currentValue));

            } catch (Exception e) {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error showing admin upgrade details target=" + targetPlayerName + " upgradeId=" + upgradeId, e);
            }

            return true;
        }

        // /isadmin upgrade <player> <upgradeId> set <level>
        if (!args[3].equalsIgnoreCase("set")) {
            return false;
        }

        String upgradeId = args[2].toLowerCase(Locale.ROOT);
        int level;

        try {
            level = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(config.getAdminUpgradeInvalidLevelMessage());
            return true;
        }

        api.setUpgradeLevel(islandUuid, upgradeId, level).thenRun(() -> {
            sender.sendMessage(config.getAdminUpgradeSetSuccessMessage(upgradeId, level));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof UpgradeDoesNotExistException) {
                sender.sendMessage(config.getPlayerUpgradeInvalidIdMessage(upgradeId));
            } else if (cause instanceof UpgradeLevelDoesNotExistException) {
                sender.sendMessage(config.getAdminUpgradeInvalidLevelMessage());
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error setting upgrade level target=" + targetPlayerName + " upgradeId=" + upgradeId + " level=" + level, ex);
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
        // /isadmin upgrade <player>
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return api.getOnlinePlayersNames().stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
        }

        // /isadmin upgrade <player> <upgradeId>
        if (args.length == 3) {
            Set<String> ids;
            try {
                ids = api.getUpgradeIds();
            } catch (Exception e) {
                return Collections.emptyList();
            }

            String prefix = args[2].toLowerCase(Locale.ROOT);
            return ids.stream().filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
        }

        // /isadmin upgrade <player> <upgradeId> set
        if (args.length == 4) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            if ("set".startsWith(prefix)) {
                return Collections.singletonList("set");
            }
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }
}