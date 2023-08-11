package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;

import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;

import java.util.Map;
import java.util.Set;

public class AdminListWorldCommand implements IslandSubCommand {
    private final NewSky plugin;

    public AdminListWorldCommand(NewSky plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(final CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /islandadmin listworlds");
            return true;
        }

        plugin.getRedisEventService().publishUpdateRequest();
        Map<String, Set<String>> serverWorlds = plugin.getRedisHandler().getAllWorlds();

        if (serverWorlds != null) {
            sender.sendMessage("Worlds updated.");
            sender.sendMessage("Worlds:");

            for (Map.Entry<String, Set<String>> entry : serverWorlds.entrySet()) {
                String serverName = entry.getKey();
                Set<String> worlds = entry.getValue();
                sender.sendMessage("Server: " + serverName);
                for (String world : worlds) {
                    sender.sendMessage("  - " + world);
                }
            }
        } else {
            sender.sendMessage("Error fetching worlds. Please try again later.");
        }
        return true;
    }
}
