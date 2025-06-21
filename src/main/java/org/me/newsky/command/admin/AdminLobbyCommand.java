package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.NoActiveServerException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class AdminLobbyCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminLobbyCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "lobby";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminLobbyAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminLobbyPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminLobbySyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminLobbyDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }

        String targetName = args[1];
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
        UUID targetUuid = targetPlayer.getUniqueId();

        api.lobby(targetUuid).thenRun(() -> sender.sendMessage(config.getAdminLobbySuccessMessage(targetName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error teleporting " + targetName + " to lobby", ex);
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