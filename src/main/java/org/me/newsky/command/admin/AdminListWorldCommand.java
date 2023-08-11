package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.handler.CacheHandler;

import java.util.Set;

public class AdminListWorldCommand implements IslandSubCommand {
    private final NewSky plugin;
    private final CacheHandler cacheHandler;

    public AdminListWorldCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
    }

    @Override
    public boolean execute(final CommandSender sender, String[] args) {
        plugin.getRedisEventService().publishUpdateRequest();
        sender.sendMessage("Update request sent.");
        sender.sendMessage("Worlds will be updated in 5 seconds...");

        new BukkitRunnable() {
            @Override
            public void run() {
                Set<String> allWorlds = plugin.getRedisHandler().getAllWorlds();
                sender.sendMessage("Worlds updated.");
                sender.sendMessage("Worlds:");
                for (String world : allWorlds) {
                    sender.sendMessage(world);
                }
            }
        }.runTaskLater(plugin, 5 * 20L); // 5 seconds, as there are 20 ticks per second

        return true;
    }
}
