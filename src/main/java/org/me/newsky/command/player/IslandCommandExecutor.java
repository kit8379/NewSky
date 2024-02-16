package org.me.newsky.command.player;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class IslandCommandExecutor implements CommandExecutor, TabCompleter {

    private final ConfigHandler config;
    private final IslandAddMemberCommand addMemberCommand;
    private final IslandRemoveMemberCommand removeMemberCommand;
    private final IslandCreateCommand createCommand;
    private final IslandDeleteCommand deleteCommand;
    private final IslandHomeCommand homeCommand;
    private final IslandSetHomeCommand setHomeCommand;
    private final IslandDelHomeCommand delHomeCommand;
    private final IslandWarpCommand warpCommand;
    private final IslandSetWarpCommand setWarpCommand;
    private final IslandDelWarpCommand delWarpCommand;
    private final IslandInfoCommand infoCommand;
    private final IslandLockCommand lockCommand;
    private final IslandPvpCommand pvpCommand;
    private final IslandSetOwnerCommand setOwnerCommand;
    private final IslandLeaveCommand leaveCommand;
    private final List<String> subCommands = Arrays.asList("addmember", "removemember", "create", "delete", "home", "sethome", "delhome", "warp", "setwarp", "delwarp", "info", "lock", "pvp", "setowner", "leave");

    public IslandCommandExecutor(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.config = config;
        this.addMemberCommand = new IslandAddMemberCommand(config, cacheHandler);
        this.removeMemberCommand = new IslandRemoveMemberCommand(config, cacheHandler);
        this.createCommand = new IslandCreateCommand(config, cacheHandler, islandHandler);
        this.deleteCommand = new IslandDeleteCommand(config, cacheHandler, islandHandler);
        this.homeCommand = new IslandHomeCommand(config, cacheHandler, islandHandler);
        this.setHomeCommand = new IslandSetHomeCommand(config, cacheHandler);
        this.delHomeCommand = new IslandDelHomeCommand(config, cacheHandler);
        this.warpCommand = new IslandWarpCommand(config, cacheHandler, islandHandler);
        this.setWarpCommand = new IslandSetWarpCommand(config, cacheHandler);
        this.delWarpCommand = new IslandDelWarpCommand(config, cacheHandler);
        this.infoCommand = new IslandInfoCommand(config, cacheHandler);
        this.lockCommand = new IslandLockCommand(config, cacheHandler);
        this.pvpCommand = new IslandPvpCommand(config, cacheHandler);
        this.setOwnerCommand = new IslandSetOwnerCommand(config, cacheHandler);
        this.leaveCommand = new IslandLeaveCommand(config, cacheHandler);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(config.getPlayerCommandHelpMessage());
            sender.sendMessage(config.getPlayerAddMemberUsageMessage());
            sender.sendMessage(config.getPlayerRemoveMemberUsageMessage());
            sender.sendMessage(config.getPlayerCreateUsageMessage());
            sender.sendMessage(config.getPlayerDeleteUsageMessage());
            sender.sendMessage(config.getPlayerHomeUsageMessage());
            sender.sendMessage(config.getPlayerSetHomeUsageMessage());
            sender.sendMessage(config.getPlayerDelHomeUsageMessage());
            sender.sendMessage(config.getPlayerWarpUsageMessage());
            sender.sendMessage(config.getPlayerSetWarpUsageMessage());
            sender.sendMessage(config.getPlayerDelWarpUsageMessage());
            sender.sendMessage(config.getPlayerInfoUsageMessage());
            sender.sendMessage(config.getPlayerLockUsageMessage());
            sender.sendMessage(config.getPlayerPvpUsageMessage());
            sender.sendMessage(config.getPlayerSetOwnerUsageMessage());
            sender.sendMessage(config.getPlayerLeaveUsageMessage());
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "addmember":
                return addMemberCommand.execute(sender, args);
            case "removemember":
                return removeMemberCommand.execute(sender, args);
            case "create":
                return createCommand.execute(sender, args);
            case "delete":
                return deleteCommand.execute(sender, args);
            case "home":
                return homeCommand.execute(sender, args);
            case "sethome":
                return setHomeCommand.execute(sender, args);
            case "delhome":
                return delHomeCommand.execute(sender, args);
            case "warp":
                return warpCommand.execute(sender, args);
            case "setwarp":
                return setWarpCommand.execute(sender, args);
            case "delwarp":
                return delWarpCommand.execute(sender, args);
            case "info":
                return infoCommand.execute(sender, args);
            case "lock":
                return lockCommand.execute(sender, args);
            case "pvp":
                return pvpCommand.execute(sender, args);
            case "setowner":
                return setOwnerCommand.execute(sender, args);
            case "leave":
                return leaveCommand.execute(sender, args);
            default:
                sender.sendMessage(config.getPlayerUnknownSubCommandMessage(subCommand));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return subCommands.stream().filter(sub -> sub.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        } else if (args.length > 1) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "home":
                    return homeCommand.onTabComplete(sender, args);
                case "warp":
                    return warpCommand.onTabComplete(sender, args);
                default:
                    break;
            }
        }
        return null;
    }
}
