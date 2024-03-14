package org.me.newsky.command.player;

import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.command.BaseCommandExecutor;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

public class PlayerCommandExecutor extends BaseCommandExecutor {

    public PlayerCommandExecutor(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(config, createCommands(config, cacheHandler, islandHandler));
    }

    private static Map<String, BaseCommand> createCommands(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        Map<String, BaseCommand> commands = new HashMap<>();
        commands.put("addmember", new PlayerAddMemberCommand(config, cacheHandler));
        commands.put("removemember", new PlayerRemoveMemberCommand(config, cacheHandler));
        commands.put("create", new PlayerCreateCommand(config, cacheHandler, islandHandler));
        commands.put("delete", new PlayerDeleteCommand(config, cacheHandler, islandHandler));
        commands.put("home", new PlayerHomeCommand(config, cacheHandler, islandHandler));
        commands.put("sethome", new PlayerSetHomeCommand(config, cacheHandler));
        commands.put("delhome", new PlayerDelHomeCommand(config, cacheHandler));
        commands.put("warp", new PlayerWarpCommand(config, cacheHandler, islandHandler));
        commands.put("setwarp", new PlayerSetWarpCommand(config, cacheHandler));
        commands.put("delwarp", new PlayerDelWarpCommand(config, cacheHandler));
        commands.put("info", new PlayerInfoCommand(config, cacheHandler));
        commands.put("lock", new PlayerLockCommand(config, cacheHandler));
        commands.put("pvp", new PlayerPvpCommand(config, cacheHandler));
        commands.put("setowner", new PlayerSetOwnerCommand(config, cacheHandler));
        commands.put("leave", new PlayerLeaveCommand(config, cacheHandler));
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
