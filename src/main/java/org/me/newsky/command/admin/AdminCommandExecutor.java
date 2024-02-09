package org.me.newsky.command.admin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AdminCommandExecutor implements CommandExecutor, TabCompleter {

    private final ConfigHandler config;
    private final AdminAddMemberCommand addMemberCommand;
    private final AdminRemoveMemberCommand removeMemberCommand;
    private final AdminCreateCommand createCommand;
    private final AdminDeleteCommand deleteCommand;
    private final AdminInfoCommand infoCommand;
    private final AdminHomeCommand homeCommand;
    private final AdminSetHomeCommand setHomeCommand;
    private final AdminDelHomeCommand delHomeCommand;
    private final AdminWarpCommand warpCommand;
    private final AdminSetWarpCommand setWarpCommand;
    private final AdminDelWarpCommand delWarpCommand;
    private final AdminLockCommand lockCommand;
    private final AdminLoadCommand loadCommand;
    private final AdminUnloadCommand unloadCommand;
    private final AdminReloadCommand reloadCommand;
    private final List<String> subCommands = Arrays.asList("addmember", "removemember", "create", "delete", "home", "sethome", "delhome", "warp", "setwarp", "delwarp", "info", "lock", "load", "unload", "reload");

    public AdminCommandExecutor(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.config = config;
        this.addMemberCommand = new AdminAddMemberCommand(config, cacheHandler);
        this.removeMemberCommand = new AdminRemoveMemberCommand(config, cacheHandler);
        this.createCommand = new AdminCreateCommand(config, cacheHandler, islandHandler);
        this.deleteCommand = new AdminDeleteCommand(config, cacheHandler, islandHandler);
        this.homeCommand = new AdminHomeCommand(config, cacheHandler, islandHandler);
        this.setHomeCommand = new AdminSetHomeCommand(config, cacheHandler);
        this.delHomeCommand = new AdminDelHomeCommand(config, cacheHandler);
        this.warpCommand = new AdminWarpCommand(config, cacheHandler, islandHandler);
        this.setWarpCommand = new AdminSetWarpCommand(config, cacheHandler);
        this.delWarpCommand = new AdminDelWarpCommand(config, cacheHandler);
        this.infoCommand = new AdminInfoCommand(config, cacheHandler);
        this.lockCommand = new AdminLockCommand(config, cacheHandler);
        this.reloadCommand = new AdminReloadCommand(plugin, config);
        this.loadCommand = new AdminLoadCommand(config, cacheHandler, islandHandler);
        this.unloadCommand = new AdminUnloadCommand(config, cacheHandler, islandHandler);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§c§l--- Island Admin Help ---");
            sender.sendMessage("§6Usage: §e/islandadmin <subcommand> [arguments]");
            sender.sendMessage("§6Available commands:");
            sender.sendMessage("§eaddmember §7- Adds a member to an island.");
            sender.sendMessage("§eremovemember §7- Removes a member from an island.");
            sender.sendMessage("§ecreate §7- Creates an island for a player.");
            sender.sendMessage("§edelete §7- Deletes an island.");
            sender.sendMessage("§einfo §7- Displays information about an island.");
            sender.sendMessage("§c§l-------------------------");
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
            case "load":
                return loadCommand.execute(sender, args);
            case "unload":
                return unloadCommand.execute(sender, args);
            case "reload":
                return reloadCommand.execute(sender, args);
            default:
                sender.sendMessage("§cUnknown subcommand.");
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
