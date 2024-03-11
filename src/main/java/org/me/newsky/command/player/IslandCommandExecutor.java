package org.me.newsky.command.player;

import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.command.BaseCommandExecutor;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

public class IslandCommandExecutor extends BaseCommandExecutor {

    public IslandCommandExecutor(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(config, createCommands(config, cacheHandler, islandHandler));
    }

    private static Map<String, BaseCommand> createCommands(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        Map<String, BaseCommand> commands = new HashMap<>();
        commands.put("addmember", new IslandAddMemberCommand(config, cacheHandler));
        commands.put("removemember", new IslandRemoveMemberCommand(config, cacheHandler));
        commands.put("create", new IslandCreateCommand(config, cacheHandler, islandHandler));
        commands.put("delete", new IslandDeleteCommand(config, cacheHandler, islandHandler));
        commands.put("home", new IslandHomeCommand(config, cacheHandler, islandHandler));
        commands.put("sethome", new IslandSetHomeCommand(config, cacheHandler));
        commands.put("delhome", new IslandDelHomeCommand(config, cacheHandler));
        commands.put("warp", new IslandWarpCommand(config, cacheHandler, islandHandler));
        commands.put("setwarp", new IslandSetWarpCommand(config, cacheHandler));
        commands.put("delwarp", new IslandDelWarpCommand(config, cacheHandler));
        commands.put("info", new IslandInfoCommand(config, cacheHandler));
        commands.put("lock", new IslandLockCommand(config, cacheHandler));
        commands.put("pvp", new IslandPvpCommand(config, cacheHandler));
        commands.put("setowner", new IslandSetOwnerCommand(config, cacheHandler));
        commands.put("leave", new IslandLeaveCommand(config, cacheHandler));
        return commands;
    }

    @Override
    protected void displayHelp(CommandSender sender) {
        sender.sendMessage(config.getPlayerCommandHelpMessage());
        subCommands.forEach(subCommand -> sender.sendMessage(config.getPlayerCommandHelpMessage()));
    }

    @Override
    protected String getUnknownSubCommandMessage(String subCommand) {
        return config.getPlayerUnknownSubCommandMessage(subCommand);
    }
}
