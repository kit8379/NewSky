package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.model.IslandTop;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * /is top
 */
public class PlayerTopCommand implements SubCommand {

    private static final int TOP_LIMIT = 10;

    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerTopCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "top";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerTopAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerTopPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerTopSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerTopDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        api.getTopIslandLevels(TOP_LIMIT).thenCompose(entries -> {
            if (entries.isEmpty()) {
                return CompletableFuture.completedFuture(new PreparedTopResult(List.of(), Map.of()));
            }

            Set<UUID> uuids = new LinkedHashSet<>();
            for (IslandTop entry : entries) {
                if (entry.getOwnerUuid() != null) {
                    uuids.add(entry.getOwnerUuid());
                }
                uuids.addAll(entry.getMembers());
            }

            if (uuids.isEmpty()) {
                return CompletableFuture.completedFuture(new PreparedTopResult(entries, Map.of()));
            }

            return api.getPlayerNames(uuids).exceptionally(ex -> {
                plugin.severe("Failed to resolve player names for /is top", ex);
                return Map.of();
            }).thenApply(names -> new PreparedTopResult(entries, names));
        }).whenComplete((result, ex) -> {
            if (result.entries().isEmpty()) {
                sender.sendMessage(config.getNoIslandsFoundMessage());
                return;
            }

            sender.sendMessage(config.getTopIslandsHeaderMessage());

            int rank = 1;
            for (IslandTop entry : result.entries()) {
                String ownerName = result.names().get(entry.getOwnerUuid());
                if (ownerName == null || ownerName.isBlank()) {
                    ownerName = entry.getOwnerUuid() != null ? entry.getOwnerUuid().toString() : "Unknown";
                }

                String membersText = formatMembers(entry.getMembers(), result.names());

                sender.sendMessage(config.getTopIslandMessage(rank, ownerName, membersText, entry.getLevel()));

                rank++;
            }
        }).exceptionally(ex -> {
            sender.sendMessage(config.getUnknownExceptionMessage());
            plugin.severe("Error showing island top for player " + sender.getName(), ex);
            return null;
        });

        return true;
    }

    private String formatMembers(Set<UUID> members, Map<UUID, String> names) {
        if (members == null || members.isEmpty()) {
            return "";
        }

        List<String> memberNames = new ArrayList<>(members.size());

        for (UUID memberUuid : members) {
            String name = names.get(memberUuid);
            if (name == null || name.isBlank()) {
                name = memberUuid.toString();
            }
            memberNames.add(name);
        }

        return String.join(", ", memberNames);
    }

    private static final class PreparedTopResult {
        private final List<IslandTop> entries;
        private final Map<UUID, String> names;

        public PreparedTopResult(List<IslandTop> entries, Map<UUID, String> names) {
            this.entries = entries;
            this.names = names;
        }

        public List<IslandTop> entries() {
            return entries;
        }

        public Map<UUID, String> names() {
            return names;
        }
    }
}