package org.me.newsky.command.sub;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;

import java.util.UUID;

public abstract class BaseSetHomeCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseSetHomeCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        if (!validateArgs(sender, args)) {
            return true;
        }

        Player player = (Player) sender;
        UUID targetUuid = getTargetUuid(sender, args);

        String homeName = args.length > getTargetHomeArgIndex() ? args[getTargetHomeArgIndex()] : "default";
        Location loc = player.getLocation();

        api.setHome(targetUuid, homeName, loc).thenRun(() -> {
            sender.sendMessage(getSetHomeSuccessMessage(args, homeName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof LocationNotInIslandException) {
                sender.sendMessage(getMustInIslandMessage(args));
            } else if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(getNoIslandMessage(args));
            } else {
                sender.sendMessage("There was an error setting the home");
                ex.printStackTrace();
            }
            return null;
        });

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract int getTargetHomeArgIndex();

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getMustInIslandMessage(String[] args);

    protected abstract String getSetHomeSuccessMessage(String[] args, String homeName);
}
