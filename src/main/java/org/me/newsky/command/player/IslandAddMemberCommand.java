package org.me.newsky.command.player;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.cache.CacheHandler;

import java.util.Optional;
import java.util.UUID;

public class IslandAddMemberCommand implements IslandSubCommand {
    private final NewSky plugin;
    private final CacheHandler cacheHandler;

    public IslandAddMemberCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /island addmember <player>");
            return true;
        }

        Player player = (Player) sender;
        OfflinePlayer targetAdd = Bukkit.getOfflinePlayer(args[1]);
        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(player.getUniqueId());

        if (islandUuid.isPresent()) {
            cacheHandler.addIslandMember(islandUuid.get(), targetAdd.getUniqueId());
            sender.sendMessage("Added " + targetAdd.getName() + " to your island.");
        } else {
            sender.sendMessage("You don't have an island.");
        }

        return true;
    }
}
