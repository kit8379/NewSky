package org.me.newsky.command.base;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.WarpDoesNotExistException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public abstract class BaseDelWarpCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseDelWarpCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        UUID targetUuid = getTargetUuid(sender, args);
        String warpName = args[getTargetWarpArgIndex()];

        api.warpAPI.delWarp(targetUuid, warpName).thenRun(() -> sender.sendMessage(getDelWarpSuccessMessage(args))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(getNoIslandMessage(args));
            } else if (ex.getCause() instanceof WarpDoesNotExistException) {
                sender.sendMessage(getNoWarpMessage(args));
            } else {
                sender.sendMessage("There was an error deleting the warp.");
                ex.printStackTrace();
            }
            return null;
        });

        return true;
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == getTargetWarpArgIndex() + 1) {
            UUID targetUuid = getTargetUuid(sender, args);
            CompletableFuture<Set<String>> warpNamesFuture = api.warpAPI.getWarpNames(targetUuid);
            try {
                Set<String> warpNames = warpNamesFuture.get();
                return warpNames.stream().filter(name -> name.toLowerCase().startsWith(args[getTargetWarpArgIndex()].toLowerCase())).collect(Collectors.toList());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract int getTargetWarpArgIndex();

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getNoWarpMessage(String[] args);

    protected abstract String getDelWarpSuccessMessage(String[] args);
}
