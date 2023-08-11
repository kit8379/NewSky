package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.cache.CacheHandler;

import java.util.Optional;
import java.util.UUID;

public class AdminAddMemberCommand implements IslandSubCommand {
    private final NewSky plugin;
    private final CacheHandler cacheHandler;

    public AdminAddMemberCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("Usage: /islandadmin addmember <player> <islandowner>");
            return true;
        }

        OfflinePlayer targetAdd = Bukkit.getOfflinePlayer(args[1]);
        OfflinePlayer targetIslandOwner = Bukkit.getOfflinePlayer(args[2]);
        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(targetIslandOwner.getUniqueId());

        if (islandUuid.isPresent()) {
            cacheHandler.addIslandMember(targetIslandOwner.getUniqueId(), targetAdd.getUniqueId());
            sender.sendMessage("Added " + targetAdd.getName() + " to " + targetIslandOwner.getName() + " island.");
        } else {
            sender.sendMessage(targetIslandOwner.getName() + " don't have an island.");
        }

        return true;
    }
}
