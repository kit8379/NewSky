package org.me.newsky.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.NewSky;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class NewSkyPlaceholderExpansion extends PlaceholderExpansion {

    private final NewSky plugin;

    public NewSkyPlaceholderExpansion(NewSky plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getAuthor() {
        return "kit8379";
    }

    @Override
    public @NotNull String getIdentifier() {
        return plugin.getName().toLowerCase(Locale.ROOT);
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
        if (player == null) {
            return null;
        }

        UUID islandUuid = plugin.getApi().getIslandUuid(player.getUniqueId());

        return resolvePlaceholder(islandUuid, identifier.toLowerCase(Locale.ROOT));
    }

    private String resolvePlaceholder(UUID islandUuid, String identifier) {
        switch (identifier) {
            case "island_uuid":
                return islandUuid.toString();
            case "island_lock":
                return String.valueOf(plugin.getApi().isIslandLock(islandUuid));
            case "island_pvp":
                return String.valueOf(plugin.getApi().isIslandPvp(islandUuid));
            case "island_level":
                return String.valueOf(plugin.getApi().getIslandLevel(islandUuid));
            case "island_owner":
                return formatUuid(plugin.getApi().getIslandOwner(islandUuid));
            case "island_members":
                return formatList(plugin.getApi().getIslandMembers(islandUuid));
            case "island_players":
                return formatList(plugin.getApi().getIslandPlayers(islandUuid));
            case "island_coops":
                return formatList(plugin.getApi().getCoopedPlayers(islandUuid));
            case "island_bans":
                return formatList(plugin.getApi().getBannedPlayers(islandUuid));
            default:
                if (identifier.startsWith("island_member_")) {
                    return formatIndexed(plugin.getApi().getIslandMembers(islandUuid), identifier, "island_member_");
                }
                if (identifier.startsWith("island_coop_")) {
                    return formatIndexed(plugin.getApi().getCoopedPlayers(islandUuid), identifier, "island_coop_");
                }
                if (identifier.startsWith("island_ban_")) {
                    return formatIndexed(plugin.getApi().getBannedPlayers(islandUuid), identifier, "island_ban_");
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
        return plugin.getApi().getPlayerName(uuid).orElse(uuid.toString());
    }

    private int parseIndex(String identifier, String prefix) {
        try {
            return Integer.parseInt(identifier.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}