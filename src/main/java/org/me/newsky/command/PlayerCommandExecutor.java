package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.player.*;
import org.me.newsky.config.ConfigHandler;

import java.util.HashMap;
import java.util.Map;

public class PlayerCommandExecutor extends BaseCommandExecutor {

    public PlayerCommandExecutor(ConfigHandler config, NewSkyAPI api) {
        super(config, createCommands(config, api));
    }

    private static Map<String, BaseCommand> createCommands(ConfigHandler config, NewSkyAPI api) {
        Map<String, BaseCommand> commands = new HashMap<>();
        commands.put("addmember", new PlayerAddMemberCommand(config, api));
        commands.put("removemember", new PlayerRemoveMemberCommand(config, api));
        commands.put("create", new PlayerCreateCommand(config, api));
        commands.put("delete", new PlayerDeleteCommand(config, api));
        commands.put("home", new PlayerHomeCommand(config, api));
        commands.put("sethome", new PlayerSetHomeCommand(config, api));
        commands.put("delhome", new PlayerDelHomeCommand(config, api));
        commands.put("warp", new PlayerWarpCommand(config, api));
        commands.put("setwarp", new PlayerSetWarpCommand(config, api));
        commands.put("delwarp", new PlayerDelWarpCommand(config, api));
        commands.put("info", new PlayerInfoCommand(config, api));
        commands.put("lock", new PlayerLockCommand(config, api));
        commands.put("pvp", new PlayerPvpCommand(config, api));
        commands.put("setowner", new PlayerSetOwnerCommand(config, api));
        commands.put("leave", new PlayerLeaveCommand(config, api));
        return commands;
    }

    @Override
    protected void displayHelp(CommandSender sender) {
        sender.sendMessage(config.getPlayerCommandHelpMessage());
        commands.forEach((commandName, subCommand) -> sender.sendMessage(subCommand.getUsageCommandMessage()));
    }

    @Override
    protected String getUnknownSubCommandMessage(String subCommand) {
        return config.getPlayerUnknownSubCommandMessage(subCommand);
    }
}
