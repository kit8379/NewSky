package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;

import java.util.UUID;

public class IslandCommand implements CommandExecutor {

    private final NewSky plugin;

    public IslandCommand(NewSky plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            displayHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                if (!player.hasPermission("newsky.island.create")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /island create <player>");
                    return true;
                }
                Player targetCreate = Bukkit.getPlayer(args[1]);
                if (targetCreate == null) {
                    player.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }
                createIsland(targetCreate.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "Island created for " + targetCreate.getName());
                break;
            case "delete":
                if (!player.hasPermission("newsky.island.delete")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /island delete <player>");
                    return true;
                }
                Player targetDelete = Bukkit.getPlayer(args[1]);
                if (targetDelete == null) {
                    player.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }
                deleteIsland(targetDelete.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "Island deleted for " + targetDelete.getName());
                break;
            case "add":
                if (!player.hasPermission("newsky.island.add")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /island add <player>");
                    return true;
                }
                Player targetAdd = Bukkit.getPlayer(args[1]);
                if (targetAdd == null) {
                    player.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }
                addIslandMember(player.getUniqueId(), targetAdd.getUniqueId());
                player.sendMessage(ChatColor.GREEN + targetAdd.getName() + " added to your island.");
                break;
            case "remove":
                if (!player.hasPermission("newsky.island.remove")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /island remove <player>");
                    return true;
                }
                Player targetRemove = Bukkit.getPlayer(args[1]);
                if (targetRemove == null) {
                    player.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }
                removeIslandMember(player.getUniqueId(), targetRemove.getUniqueId());
                player.sendMessage(ChatColor.GREEN + targetRemove.getName() + " removed from your island.");
                break;
            case "reload":
                if (!player.hasPermission("newsky.island.reload")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                plugin.reloadConfig();
                player.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
                break;
            case "save":
                if (!player.hasPermission("newsky.island.save")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                saveCacheToDatabase();
                player.sendMessage(ChatColor.GREEN + "Cache saved to database.");
                break;
            default:
                displayHelp(player);
                break;
        }

        return true;
    }

    private void displayHelp(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Island Commands:");
        player.sendMessage("/island - Display help list");
        player.sendMessage("/island create <player> - Create an island for a player");
        player.sendMessage("/island delete <player> - Delete an island for a player");
        player.sendMessage("/island add <player> - Add a player to your island");
        player.sendMessage("/island remove <player> - Remove a player from your island");
        player.sendMessage("/island reload - Reload config");
        player.sendMessage("/island save - Force save cache to database");
    }

    private void createIsland(UUID uuid) {
        // Implementation for creating island
        // You'll likely want to call methods from the CacheHandler or another appropriate class here.
    }

    private void deleteIsland(UUID uuid) {
        // Implementation for deleting island
    }

    private void addIslandMember(UUID islandOwner, UUID memberUuid) {
        // Implementation for adding a member to an island
    }

    private void removeIslandMember(UUID islandOwner, UUID memberUuid) {
        // Implementation for removing a member from an island
    }

    private void saveCacheToDatabase() {
        // Implementation for saving cache to the database
    }
}
