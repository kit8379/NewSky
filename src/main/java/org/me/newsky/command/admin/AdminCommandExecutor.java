package org.me.newsky.command.admin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;

public class AdminCommandExecutor implements CommandExecutor {

    private final CacheHandler cacheHandler;
    private final IslandHandler islandHandler;
    private final AdminAddMemberCommand addMemberCommand;
    private final AdminRemoveMemberCommand removeMemberCommand;
    private final AdminCreateCommand createCommand;
    private final AdminDeleteCommand deleteCommand;
    private final AdminInfoCommand infoCommand;

    public AdminCommandExecutor(CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
        this.addMemberCommand = new AdminAddMemberCommand(cacheHandler, islandHandler);
        this.removeMemberCommand = new AdminRemoveMemberCommand(cacheHandler);
        this.createCommand = new AdminCreateCommand(cacheHandler, islandHandler);
        this.deleteCommand = new AdminDeleteCommand(cacheHandler, islandHandler);
        this.infoCommand = new AdminInfoCommand(cacheHandler);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
            default:
                sender.sendMessage("Unknown admin command. Please refer to the documentation for a list of valid commands.");
                return true;
        }
    }
}
