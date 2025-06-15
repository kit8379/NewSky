package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.IslandNotLoadedException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * /isadmin unload <player>
 */
public class AdminUnloadCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminUnloadCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "unload";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminUnloadAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminUnloadPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminUnloadSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminUnloadDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }

        String targetPlayerName = args[1];
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(targetUuid);
        } catch (IslandDoesNotExistException e) {
            sender.sendMessage(config.getNoIslandMessage(targetPlayerName));
            return true;
        }

        api.unloadIsland(islandUuid).thenRun(() -> sender.sendMessage(config.getIslandUnloadSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandNotLoadedException) {
                sender.sendMessage(config.getIslandNotLoadedMessage());
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.getLogger().log(Level.SEVERE, "Error unloading island for " + targetPlayerName, ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return api.getOnlinePlayers().stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}