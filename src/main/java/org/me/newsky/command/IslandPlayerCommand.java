package org.me.newsky.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.player.*;
import org.me.newsky.config.ConfigHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * /is
 */
public class IslandPlayerCommand implements CommandExecutor, AsyncCommandTabRouter {
    private final ConfigHandler config;
    private final Map<String, SubCommand> subCommandMap = new HashMap<>();
    private final Set<SubCommand> subCommands = new HashSet<>();

    public IslandPlayerCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.config = config;

        subCommands.add(new PlayerCreateIslandCommand(plugin, api, config));
        subCommands.add(new PlayerDeleteIslandCommand(plugin, api, config));
        subCommands.add(new PlayerInviteCommand(plugin, api, config));
        subCommands.add(new PlayerAcceptInviteCommand(plugin, api, config));
        subCommands.add(new PlayerRejectInviteCommand(plugin, api, config));
        subCommands.add(new PlayerRemoveMemberCommand(plugin, api, config));
        subCommands.add(new PlayerHomeCommand(plugin, api, config));
        subCommands.add(new PlayerSetHomeCommand(plugin, api, config));
        subCommands.add(new PlayerDelHomeCommand(plugin, api, config));
        subCommands.add(new PlayerWarpCommand(plugin, api, config));
        subCommands.add(new PlayerSetWarpCommand(plugin, api, config));
        subCommands.add(new PlayerDelWarpCommand(plugin, api, config));
        subCommands.add(new PlayerSetOwnerCommand(plugin, api, config));
        subCommands.add(new PlayerLeaveCommand(plugin, api, config));
        subCommands.add(new PlayerLevelCommand(plugin, api, config));
        subCommands.add(new PlayerValueCommand(config));
        subCommands.add(new PlayerLockCommand(plugin, api, config));
        subCommands.add(new PlayerPvpCommand(plugin, api, config));
        subCommands.add(new PlayerTopCommand(plugin, api, config));
        subCommands.add(new PlayerInfoCommand(plugin, api, config));
        subCommands.add(new PlayerExpelCommand(plugin, api, config));
        subCommands.add(new PlayerBanCommand(plugin, api, config));
        subCommands.add(new PlayerUnbanCommand(plugin, api, config));
        subCommands.add(new PlayerBanListCommand(plugin, api, config));
        subCommands.add(new PlayerCoopCommand(plugin, api, config));
        subCommands.add(new PlayerUncoopCommand(plugin, api, config));
        subCommands.add(new PlayerCoopListCommand(plugin, api, config));
        subCommands.add(new PlayerUpgradeCommand(plugin, api, config));
        subCommands.add(new PlayerLobbyCommand(plugin, api, config));
        subCommands.add(new PlayerHelpCommand(config, subCommands));

        for (SubCommand cmd : subCommands) {
            registerSubCommand(cmd);
        }
    }

    private void registerSubCommand(SubCommand cmd) {
        subCommandMap.put(cmd.getName().toLowerCase(Locale.ROOT), cmd);
        for (String alias : cmd.getAliases()) {
            subCommandMap.put(alias.toLowerCase(Locale.ROOT), cmd);
        }
    }

    private List<String> getRootSuggestions(CommandSender sender, String arg0) {
        String prefix = arg0.toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();

        for (SubCommand cmd : subCommands) {
            String perm = cmd.getPermission();
            if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) {
                continue;
            }

            String name = cmd.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                suggestions.add(name);
            }

            for (String alias : cmd.getAliases()) {
                if (alias.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    suggestions.add(alias);
                }
            }
        }

        return suggestions;
    }

    @Override
    public CompletableFuture<List<String>> completeAsync(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        if (args.length == 1) {
            return CompletableFuture.completedFuture(getRootSuggestions(sender, args[0]));
        }

        SubCommand target = subCommandMap.get(args[0].toLowerCase(Locale.ROOT));
        if (target == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String perm = target.getPermission();
        if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        if (target instanceof AsyncTabComplete asyncTabComplete) {
            return asyncTabComplete.tabCompleteAsync(sender, label, args).thenApply(list -> list == null ? Collections.<String>emptyList() : list).exceptionally(ex -> Collections.emptyList());
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("newsky.island.player")) {
            sender.sendMessage(config.getNoPermissionCommandMessage());
            return true;
        }

        if (args.length == 0) {
            String mode = config.getBaseCommandMode();
            if ("island".equalsIgnoreCase(mode)) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
                    return true;
                }

                SubCommand homeCmd = subCommandMap.get("home");
                return homeCmd.execute(player, new String[]{"home"});
            } else {
                SubCommand helpCmd = subCommandMap.get("help");
                if (helpCmd != null) {
                    return helpCmd.execute(sender, new String[]{"help"});
                }
                return true;
            }
        }

        String subName = args[0].toLowerCase(Locale.ROOT);
        SubCommand target = subCommandMap.get(subName);
        if (target == null) {
            sender.sendMessage(config.getPlayerUnknownSubCommandMessage());
            return true;
        }

        String perm = target.getPermission();
        if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) {
            sender.sendMessage(config.getNoPermissionCommandMessage());
            return true;
        }

        boolean success = target.execute(sender, args);
        if (!success) {
            sender.sendMessage(config.getPlayerCommandUsageMessage(target.getName(), target.getSyntax()));
        }
        return true;
    }
}