package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.sub.admin.*;
import org.me.newsky.config.ConfigHandler;

import java.util.HashMap;
import java.util.Map;

public class AdminCommandExecutor extends BaseCommandExecutor {

    public AdminCommandExecutor(NewSky plugin, ConfigHandler config, NewSkyAPI api) {
        super(config, createCommands(plugin, config, api));
    }

    private static Map<String, BaseCommand> createCommands(NewSky plugin, ConfigHandler config, NewSkyAPI api) {
        Map<String, BaseCommand> commands = new HashMap<>();
        commands.put("addmember", new AdminAddMemberCommand(config, api));
        commands.put("removemember", new AdminRemoveMemberCommand(config, api));
        commands.put("create", new AdminCreateCommand(config, api));
        commands.put("delete", new AdminDeleteCommand(config, api));
        commands.put("info", new AdminInfoCommand(config, api));
        commands.put("home", new AdminHomeCommand(config, api));
        commands.put("sethome", new AdminSetHomeCommand(config, api));
        commands.put("delhome", new AdminDelHomeCommand(config, api));
        commands.put("warp", new AdminWarpCommand(config, api));
        commands.put("setwarp", new AdminSetWarpCommand(config, api));
        commands.put("delwarp", new AdminDelWarpCommand(config, api));
        commands.put("lock", new AdminLockCommand(config, api));
        commands.put("pvp", new AdminPvpCommand(config, api));
        commands.put("setowner", new AdminSetOwnerCommand(config, api));
        commands.put("load", new AdminLoadCommand(config, api));
        commands.put("unload", new AdminUnloadCommand(config, api));
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
