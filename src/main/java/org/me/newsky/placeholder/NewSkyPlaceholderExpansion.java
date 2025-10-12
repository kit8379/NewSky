package org.me.newsky.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class NewSkyPlaceholderExpansion extends PlaceholderExpansion {

    private final NewSky plugin;
    private final Cache cache;

    public NewSkyPlaceholderExpansion(NewSky plugin, Cache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    @Override
    public @NotNull String getAuthor() {
        return "kit8379";
    }

    @Override
    public @NotNull String getIdentifier() {
        return plugin.getName().toLowerCase();
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return null;

        Optional<UUID> islandUuidOpt = cache.getIslandUuid(player.getUniqueId());
        if (islandUuidOpt.isEmpty()) {
            return null;
        }

        UUID islandUuid = islandUuidOpt.get();
        return resolvePlaceholder(islandUuid, identifier.toLowerCase());
    }

    private String resolvePlaceholder(UUID islandUuid, String identifier) {
        switch (identifier) {
            case "island_uuid":
                return islandUuid.toString();
            case "island_lock":
                return String.valueOf(cache.isIslandLock(islandUuid));
            case "island_pvp":
                return String.valueOf(cache.isIslandPvp(islandUuid));
            case "island_level":
                return String.valueOf(cache.getIslandLevel(islandUuid));
            case "island_owner":
                return formatUuid(cache.getIslandOwner(islandUuid));
            case "island_members":
                return formatList(cache.getIslandMembers(islandUuid));
            case "island_players":
                return formatList(cache.getIslandPlayers(islandUuid));
            case "island_coops":
                return formatList(cache.getCoopedPlayers(islandUuid));
            case "island_bans":
                return formatList(cache.getBannedPlayers(islandUuid));
            default:
                if (identifier.startsWith("island_member_")) {
                    return formatIndexed(cache.getIslandMembers(islandUuid), identifier, "island_member_");
                }
                if (identifier.startsWith("island_coops_")) {
                    return formatIndexed(cache.getCoopedPlayers(islandUuid), identifier, "island_coops_");
                }
                if (identifier.startsWith("island_bans_")) {
                    return formatIndexed(cache.getBannedPlayers(islandUuid), identifier, "island_bans_");
                }
                return null;
        }
    }

    private String formatList(Set<UUID> uuids) {
        return uuids.stream().map(this::formatUuid).collect(Collectors.joining(", "));
    }

    private String formatIndexed(Set<UUID> uuids, String identifier, String prefix) {
        int index = parseIndex(identifier, prefix);
        return formatIndexed(uuids, index);
    }

    private String formatIndexed(Set<UUID> uuids, int index) {
        if (index < 0 || index >= uuids.size()) {
            return null;
        }
        return formatUuid(new ArrayList<>(uuids).get(index));
    }

    private String formatUuid(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return cache.getPlayerName(uuid).orElse(uuid.toString());
    }

    private int parseIndex(String identifier, String prefix) {
        try {
            return Integer.parseInt(identifier.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}