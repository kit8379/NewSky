package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.UpgradeDoesNotExistException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        // /isadmin upgrade
        // Show overview
        if (args.length <= 1) {
            // TODO: Implement overview of all upgrades
            return true;
        }

        // /isadmin upgrade <player> ...
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
            // TODO: Implement showing specific upgrade details
            return true;
        }

        // /isadmin upgrade <player> <upgradeId> set <level>
        if (args.length == 5) {
            String upgradeId = args[2].toLowerCase();

            if (!args[3].equalsIgnoreCase("set")) {
                return false;
            }

            int level;
            try {
                level = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage(config.getAdminUpgradeInvalidLevelMessage());
                return true;
            }

            api.setIslandUpgradeLevel(islandUuid, upgradeId, level).thenRun(() -> {
                sender.sendMessage(config.getAdminUpgradeSetSuccessMessage(upgradeId, level));
            }).exceptionally(ex -> {
                Throwable cause = ex.getCause();
                if (cause instanceof UpgradeDoesNotExistException) {
                    sender.sendMessage(config.getPlayerUpgradeInvalidIdMessage(upgradeId));
                } else {
                    sender.sendMessage(config.getUnknownExceptionMessage());
                    plugin.severe("Error setting upgrade level target=" + targetPlayerName + " upgradeId=" + upgradeId + " level=" + level, ex);
                }
                return null;
            });

            return true;
        }

        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        // TODO: Implement tab completion
        return Collections.emptyList();
    }
}