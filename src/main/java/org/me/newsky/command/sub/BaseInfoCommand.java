package org.me.newsky.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class BaseInfoCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseInfoCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if ((sender instanceof Player && (args.length < 1 || args.length > 2)) || (!(sender instanceof Player) && args.length != 2)) {
            sender.sendMessage(config.getUsagePrefix() + getUsageCommandMessage());
            return true;
        }

        UUID targetUuid = args.length > 1 ? Bukkit.getOfflinePlayer(args[1]).getUniqueId() : ((Player) sender).getUniqueId();

        CompletableFuture<Integer> levelFuture = api.getIslandLevel(targetUuid);
        CompletableFuture<UUID> ownerFuture = api.getIslandOwner(targetUuid);
        CompletableFuture<Set<UUID>> membersFuture = api.getIslandMembers(targetUuid);

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(levelFuture, ownerFuture, membersFuture);

        allFutures.thenRun(() -> {
            int level = levelFuture.join();
            UUID owner = ownerFuture.join();
            Set<UUID> members = membersFuture.join();

            sender.sendMessage("Island Information:");
            sender.sendMessage("Island Owner: " + owner.toString());
            sender.sendMessage("Island Level: " + level);
            sender.sendMessage("Island Members: " + members.size() + " members.");
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error retrieving island information.");
            ex.printStackTrace();
            return null;
        });

        return true;
    }

    public abstract String getUsageCommandMessage();
}
