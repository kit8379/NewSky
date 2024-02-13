package org.me.newsky.command;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseSetWarpCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseSetWarpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!validateArgs(sender, args)) {
            return true;
        }

        Player player = (Player) sender;
        UUID targetUuid = getTargetUuid(sender, args);

        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        if (!player.getWorld().getName().equals("island-" + islandUuid)) {
            sender.sendMessage(getMustInIslandMessage(args));
            return true;
        }

        String warpName = args.length > getTargetWarpArgIndex() ? args[getTargetWarpArgIndex()] : "default";
        Location loc = player.getLocation();
        String warpLocation = String.format("%.1f,%.1f,%.1f,%.1f,%.1f", loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());

        cacheHandler.addOrUpdateWarpPoint(targetUuid, islandUuid, warpName, warpLocation);
        sender.sendMessage(getSetWarpSuccessMessage(args, warpName));

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract int getTargetWarpArgIndex();

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getMustInIslandMessage(String[] args);

    protected abstract String getSetWarpSuccessMessage(String[] args, String warpName);
}
