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

    public AdminInfoCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if(args.length != 3) {
            sender.sendMessage("Usage: /island info <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);

        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(target.getUniqueId());

        if (islandUuid.isPresent()) {
            Set<UUID> memberUuids = cacheHandler.getIslandMembers(islandUuid.get());
            StringBuilder membersString = new StringBuilder();

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
        } else {
            sender.sendMessage("Player does not have an island.");
        }

        return true;
    }
}

