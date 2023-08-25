package org.me.newsky.command.player;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import org.jetbrains.annotations.NotNull;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.island.IslandHandler;


public class IslandCommandExecutor implements CommandExecutor {

    private final IslandAddMemberCommand addMemberCommand;
    private final IslandRemoveMemberCommand removeMemberCommand;
    private final IslandCreateCommand createCommand;
    private final IslandDeleteCommand deleteCommand;
    private final IslandInfoCommand infoCommand;

    public IslandCommandExecutor(CacheHandler cacheHandler, IslandHandler islandHandler) {

        this.addMemberCommand = new IslandAddMemberCommand(cacheHandler, islandHandler);
        this.removeMemberCommand = new IslandRemoveMemberCommand(cacheHandler);
        this.createCommand = new IslandCreateCommand(cacheHandler, islandHandler);
        this.deleteCommand = new IslandDeleteCommand(cacheHandler, islandHandler);
        this.infoCommand = new IslandInfoCommand(cacheHandler);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§c§l--- Island Help ---");
            sender.sendMessage("§6Usage: §e/island <subcommand> [arguments]");
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
                sender.sendMessage("Unknown command. Please refer to the documentation for a list of valid commands.");
                return true;
        }
    }
}
