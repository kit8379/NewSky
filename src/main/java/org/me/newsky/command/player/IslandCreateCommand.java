package org.me.newsky.command.player;

import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.cache.CacheHandler;

public class IslandCreateCommand implements IslandSubCommand {
    private final NewSky plugin;
    private final CacheHandler cacheHandler;

    public IslandCreateCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if(args.length != 1) {
            sender.sendMessage("Usage: /island create");
            return true;
        }

        Player player = (Player) sender;

        cacheHandler.createIsland(player.getUniqueId());
        sender.sendMessage("Island created.");
        return true;
    }
}
