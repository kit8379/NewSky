package org.me.newsky.command.admin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

public class AdminCommandExecutor implements CommandExecutor {

    private final ConfigHandler config;
    private final AdminAddMemberCommand addMemberCommand;
    private final AdminRemoveMemberCommand removeMemberCommand;
    private final AdminCreateCommand createCommand;
    private final AdminDeleteCommand deleteCommand;
    private final AdminInfoCommand infoCommand;
    private final AdminHomeCommand homeCommand;

    public AdminCommandExecutor(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.config = config;
        this.addMemberCommand = new AdminAddMemberCommand(config, cacheHandler, islandHandler);
        this.removeMemberCommand = new AdminRemoveMemberCommand(config, cacheHandler);
        this.createCommand = new AdminCreateCommand(config, cacheHandler, islandHandler);
        this.deleteCommand = new AdminDeleteCommand(config, cacheHandler, islandHandler);
        this.infoCommand = new AdminInfoCommand(config, cacheHandler);
        this.homeCommand = new AdminHomeCommand(config, cacheHandler, islandHandler);
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
            case "info":
                return infoCommand.execute(sender, args);
            case "home":
                return homeCommand.execute(sender, args);
            default:
                sender.sendMessage(config.getUnknownCommandMessage());
                return true;
        }
    }
}
