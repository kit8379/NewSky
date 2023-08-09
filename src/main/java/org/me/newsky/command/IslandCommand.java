package org.me.newsky.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.handler.CacheHandler;

import java.util.UUID;

public class IslandCommand implements CommandExecutor {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;

    public IslandCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
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
                    player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /island create <player>", NamedTextColor.RED));
                    return true;
                }
                Player targetCreate = Bukkit.getPlayer(args[1]);
                if (targetCreate == null) {
                    player.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
                    return true;
                }
                createIsland(targetCreate.getUniqueId());
                player.sendMessage(Component.text("Island created for " + targetCreate.getName(), NamedTextColor.GREEN));
                break;
            case "delete":
                if (!player.hasPermission("newsky.island.delete")) {
                    player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /island delete <player>", NamedTextColor.RED));
                    return true;
                }
                Player targetDelete = Bukkit.getPlayer(args[1]);
                if (targetDelete == null) {
                    player.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
                    return true;
                }
                deleteIsland(targetDelete.getUniqueId());
                player.sendMessage(Component.text("Island deleted for " + targetDelete.getName(), NamedTextColor.GREEN));
                break;
            case "add":
                if (!player.hasPermission("newsky.island.add")) {
                    player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /island add <player>", NamedTextColor.RED));
                    return true;
                }
                Player targetAdd = Bukkit.getPlayer(args[1]);
                if (targetAdd == null) {
                    player.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
                    return true;
                }
                addIslandMember(player.getUniqueId(), targetAdd.getUniqueId());
                player.sendMessage(Component.text(targetAdd.getName() + " added to your island.", NamedTextColor.GREEN));
                break;
            case "remove":
                if (!player.hasPermission("newsky.island.remove")) {
                    player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /island remove <player>", NamedTextColor.RED));
                    return true;
                }
                Player targetRemove = Bukkit.getPlayer(args[1]);
                if (targetRemove == null) {
                    player.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
                    return true;
                }
                removeIslandMember(player.getUniqueId(), targetRemove.getUniqueId());
                player.sendMessage(Component.text(targetRemove.getName() + " removed from your island.", NamedTextColor.GREEN));
                break;
            case "reload":
                if (!player.hasPermission("newsky.island.reload")) {
                    player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
                    return true;
                }
                plugin.reloadConfig();
                player.sendMessage(Component.text("Configuration reloaded.", NamedTextColor.GREEN));
                break;
            case "save":
                if (!player.hasPermission("newsky.island.save")) {
                    player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
                    return true;
                }
                saveCacheToDatabase();
                player.sendMessage(Component.text("Cache saved to database.", NamedTextColor.GREEN));
                break;
            default:
                displayHelp(player);
                break;
        }

        return true;
    }

    private void displayHelp(Player player) {
        player.sendMessage(Component.text("Island Commands:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/island - Display help list"));
        player.sendMessage(Component.text("/island create <player> - Create an island for a player"));
        player.sendMessage(Component.text("/island delete <player> - Delete an island for a player"));
        player.sendMessage(Component.text("/island add <player> - Add a player to your island"));
        player.sendMessage(Component.text("/island remove <player> - Remove a player from your island"));
        player.sendMessage(Component.text("/island reload - Reload config"));
        player.sendMessage(Component.text("/island save - Force save cache to database"));
    }

    private void createIsland(UUID uuid) {
        // Implementation for creating island
        // You'll likely want to call methods from the CacheHandler or another appropriate class here.
        cacheHandler.createIsland(uuid);
    }

    private void deleteIsland(UUID uuid) {
        // Implementation for deleting island
        cacheHandler.deleteIsland(uuid);
    }

    private void addIslandMember(UUID islandOwner, UUID memberUuid) {
        // Implementation for adding a member to an island
        cacheHandler.addIslandMember(islandOwner, memberUuid);
    }

    private void removeIslandMember(UUID islandOwner, UUID memberUuid) {
        // Implementation for removing a member from an island
        cacheHandler.removeIslandMember(islandOwner, memberUuid);
    }

    private void saveCacheToDatabase() {
        // Implementation for saving cache to the database
        cacheHandler.saveCacheToDatabase();
    }
}
