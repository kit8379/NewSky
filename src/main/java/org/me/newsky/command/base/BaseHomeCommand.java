package org.me.newsky.command.base;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class BaseHomeCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseHomeCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check if the sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        // Validate the command arguments
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the target UUID
        UUID targetUuid = getTargetUuid(sender, args);

        // Teleport the player to the home location
        String homeName = args.length > getTargetHomeArgIndex() ? args[getTargetHomeArgIndex()] : "default";
        api.homeAPI.home(targetUuid, homeName)
                .thenRun(() -> sender.sendMessage(getIslandHomeSuccessMessage(homeName)))
                .exceptionally(ex -> {
                    sender.sendMessage(ex.getMessage());
                    return null;
                });

        return true;
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == getTargetHomeArgIndex() + 1 && sender instanceof Player) {
            UUID targetUuid = ((Player) sender).getUniqueId();
            try {
                Set<String> homeNames = api.homeAPI.getHomeNames(targetUuid).get();
                return homeNames.stream()
                        .filter(name -> name.toLowerCase().startsWith(args[getTargetHomeArgIndex()].toLowerCase()))
                        .collect(Collectors.toList());
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

    protected abstract String getIslandHomeSuccessMessage(String homeName);
}
