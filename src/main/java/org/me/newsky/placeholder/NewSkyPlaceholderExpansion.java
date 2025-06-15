package org.me.newsky.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;

import java.util.Optional;
import java.util.UUID;

public class NewSkyPlaceholderExpansion extends PlaceholderExpansion {

    private final NewSky plugin;
    private final Cache cache;

    public NewSkyPlaceholderExpansion(NewSky plugin, Cache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getIdentifier() {
        return plugin.getName();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
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

        if (identifier.equalsIgnoreCase("island_level")) {
            Optional<UUID> islandUuid = cache.getIslandUuid(player.getUniqueId());
            return islandUuid.map(uuid -> String.valueOf(cache.getIslandLevel(uuid))).orElse(null);
        } else if (identifier.equalsIgnoreCase("island_uuid")) {
            Optional<UUID> islandUuid = cache.getIslandUuid(player.getUniqueId());
            return islandUuid.map(UUID::toString).orElse(null);
        } else if (identifier.equalsIgnoreCase("island_owner")) {
            Optional<UUID> islandUuid = cache.getIslandUuid(player.getUniqueId());
            return islandUuid.map(uuid -> cache.getIslandOwner(uuid).toString()).orElse(null);
        }

        return null;
    }
}
