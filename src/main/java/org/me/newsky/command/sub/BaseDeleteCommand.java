package org.me.newsky.command.sub;

import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.command.Confirmation;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.UUID;

public abstract class BaseDeleteCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;
    private final Confirmation confirmations = new Confirmation();

    public BaseDeleteCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check if the command arguments are valid
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the target player's UUID
        UUID targetUuid = getTargetUuid(sender, args);

        // Double confirmation check
        String commandName = "delete"; // Use a constant or a method to get the command name
        if (commandName.equals(confirmations.getIfPresent(targetUuid))) {
            confirmations.invalidate(targetUuid);
            // Delete the island
            api.deleteIsland(targetUuid).thenRun(() -> {
                sender.sendMessage(getIslandDeleteSuccessMessage(args));
            }).exceptionally(ex -> {
                if (ex.getCause() instanceof IslandDoesNotExistException) {
                    sender.sendMessage(getNoIslandMessage(args));
                } else {
                    sender.sendMessage("There was an error deleting the island");
                    ex.printStackTrace();
                }
                return null;
            });
        } else {
            confirmations.put(targetUuid, commandName);
            sender.sendMessage(getIslandDeleteWarningMessage(args));
        }

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getIslandDeleteWarningMessage(String[] args);

    protected abstract String getIslandDeleteSuccessMessage(String[] args);
}
