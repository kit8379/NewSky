package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.admin.*;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.HashMap;
import java.util.Map;

public class AdminCommandExecutor extends BaseCommandExecutor {

    public AdminCommandExecutor(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(config, createCommands(plugin, config, cacheHandler, islandHandler));
    }

    private static Map<String, BaseCommand> createCommands(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        Map<String, BaseCommand> commands = new HashMap<>();
        commands.put("addmember", new AdminAddMemberCommand(config, cacheHandler));
        commands.put("removemember", new AdminRemoveMemberCommand(config, cacheHandler));
        commands.put("create", new AdminCreateCommand(config, cacheHandler, islandHandler));
        commands.put("delete", new AdminDeleteCommand(config, cacheHandler, islandHandler));
        commands.put("info", new AdminInfoCommand(config, cacheHandler));
        commands.put("home", new AdminHomeCommand(config, cacheHandler, islandHandler));
        commands.put("sethome", new AdminSetHomeCommand(config, cacheHandler));
        commands.put("delhome", new AdminDelHomeCommand(config, cacheHandler));
        commands.put("warp", new AdminWarpCommand(config, cacheHandler, islandHandler));
        commands.put("setwarp", new AdminSetWarpCommand(config, cacheHandler));
        commands.put("delwarp", new AdminDelWarpCommand(config, cacheHandler));
        commands.put("lock", new AdminLockCommand(config, cacheHandler, islandHandler));
        commands.put("pvp", new AdminPvpCommand(config, cacheHandler));
        commands.put("setowner", new AdminSetOwnerCommand(config, cacheHandler));
        commands.put("load", new AdminLoadCommand(config, cacheHandler, islandHandler));
        commands.put("unload", new AdminUnloadCommand(config, cacheHandler, islandHandler));
        commands.put("reload", new AdminReloadCommand(plugin, config));
        return commands;
    }

    @Override
    protected void displayHelp(CommandSender sender) {
        sender.sendMessage(config.getAdminCommandHelpMessage());
        commands.forEach((commandName, subCommand) -> sender.sendMessage(subCommand.getUsageCommandMessage()));
    }

    @Override
    protected String getUnknownSubCommandMessage(String subCommand) {
        return config.getAdminUnknownSubCommandMessage(subCommand);
    }
}
