package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.IslandSubCommand;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class AdminInfoCommand implements IslandSubCommand {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;

    public AdminInfoCommand(NewSky plugin, CacheHandler cacheHandler) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);

        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(target.getUniqueId());

        // Check if player have an island
        if (islandUuid.isEmpty()) {
            sender.sendMessage("Player does not have an island.");
            return true;
        }

        // Get all the members of the island.
        Set<UUID> memberUuids = cacheHandler.getIslandMembers(islandUuid.get());
        StringBuilder membersString = new StringBuilder();

        // Build a string of all the members.
        for (UUID memberUuid : memberUuids) {
            String memberName = Bukkit.getOfflinePlayer(memberUuid).getName();
            membersString.append(memberName).append(", ");
        }

        // Remove the trailing comma and space if any members were found.
        if (membersString.length() > 0) {
            membersString = new StringBuilder(membersString.substring(0, membersString.length() - 2));
        }

        sender.sendMessage("Island Info");
        sender.sendMessage("Island UUID: " + islandUuid.get());
        sender.sendMessage("Island Owner: " + Bukkit.getOfflinePlayer(cacheHandler.getIslandOwner(islandUuid.get())).getName());
        sender.sendMessage("Island Members: " + membersString);

        return true;
    }
}