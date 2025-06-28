package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.HomeDoesNotExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /isadmin delhome <player> <home>
 */
public class AdminDelHomeCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminDelHomeCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "delhome";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminDelHomeAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminDelHomePermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminDelHomeSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminDelHomeDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String homePlayerName = args[1];
        String homeName = args[2];

        Optional<UUID> targetUuidOpt = api.getPlayerUuid(homePlayerName);
        if (targetUuidOpt.isEmpty()) {
            sender.sendMessage(config.getUnknownPlayerMessage(homePlayerName));
            return true;
        }
        UUID targetUuid = targetUuidOpt.get();

        if ("default".equalsIgnoreCase(homeName)) {
            sender.sendMessage(config.getAdminCannotDeleteDefaultHomeMessage(homePlayerName));
            return true;
        }

        api.delHome(targetUuid, homeName).thenRun(() -> sender.sendMessage(config.getAdminDelHomeSuccessMessage(homePlayerName, homeName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(homePlayerName));
            } else if (cause instanceof HomeDoesNotExistException) {
                sender.sendMessage(config.getAdminNoHomeMessage(homePlayerName, homeName));
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error deleting home " + homeName + " for " + homePlayerName, ex);
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

        if (args.length == 3) {
            Optional<UUID> uuidOpt = api.getPlayerUuid(args[1]);
            if (uuidOpt.isPresent()) {
                return api.getHomeNames(uuidOpt.get()).stream().filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
