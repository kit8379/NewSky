package org.me.newsky.command.base;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.HomeDoesNotExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public abstract class BaseDelHomeCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseDelHomeCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the target player's UUID
        UUID targetUuid = getTargetUuid(sender, args);

        // Get the target home name
        String homeName = args[getTargetHomeArgIndex()];

        // Check if the player target home point is default
        if (homeName.equals("default")) {
            sender.sendMessage(getCannotDeleteDefaultHomeMessage(args));
            return true;
        }

        // Delete the home point
        api.homeAPI.delHome(targetUuid, homeName).thenRun(() -> sender.sendMessage(getDelHomeSuccessMessage(args))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(getNoIslandMessage(args));
            } else if (ex.getCause() instanceof HomeDoesNotExistException) {
                sender.sendMessage(getNoHomeMessage(args));
            } else {
                sender.sendMessage("There was an error deleting the home");
                ex.printStackTrace();
            }
            return null;
        });

        return true;
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == getTargetHomeArgIndex() + 1) {
            UUID targetUuid = getTargetUuid(sender, args);
            CompletableFuture<Set<String>> homeNamesFuture = api.homeAPI.getHomeNames(targetUuid);
            try {
                Set<String> homeNames = homeNamesFuture.get();
                return homeNames.stream().filter(name -> name.toLowerCase().startsWith(args[getTargetHomeArgIndex()].toLowerCase())).collect(Collectors.toList());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract int getTargetHomeArgIndex();

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getNoHomeMessage(String[] args);

    protected abstract String getCannotDeleteDefaultHomeMessage(String[] args);

    protected abstract String getDelHomeSuccessMessage(String[] args);
}
