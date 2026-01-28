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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

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

        UUID playerUuid = player.getUniqueId();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(playerUuid);
        } catch (IslandDoesNotExistException e) {
            player.sendMessage(config.getPlayerNoIslandMessage());
            return true;
        }

        // /is upgrade
        // Show upgrade overview
        if (args.length == 1) {
            // TODO: Implement upgrade overview
            return true;
        }

        // /is upgrade <upgradeId>
        // Show specific upgrade details
        if (args.length == 2) {
            // TODO: Implement specific upgrade details
            return true;
        }

        // /is upgrade <upgradeId> buy
        if (args.length == 3) {
            if (!args[2].equalsIgnoreCase("buy")) {
                return false;
            }

            String upgradeId = args[1].toLowerCase();

            api.upgradeToNextLevel(islandUuid, upgradeId).thenAccept(result -> {
                player.sendMessage(config.getPlayerUpgradeBuySuccessMessage(result.getUpgradeId(), result.getOldLevel(), result.getNewLevel(), result.getRequireIslandLevel(), result.getOldValue(), result.getNewValue()));
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

        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        // TODO: Implement tab completion
        return Collections.emptyList();
    }
}
