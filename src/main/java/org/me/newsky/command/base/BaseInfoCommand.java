package org.me.newsky.command.base;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class BaseInfoCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseInfoCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {

        return true;
    }

    private String buildMembersString(Set<UUID> memberUuids) {
        return memberUuids.stream().map(uuid -> Bukkit.getOfflinePlayer(uuid).getName()).collect(Collectors.joining(", "));
    }

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);
}
