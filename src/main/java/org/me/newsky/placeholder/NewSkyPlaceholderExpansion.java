package org.me.newsky.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;

public class NewSkyPlaceholderExpansion extends PlaceholderExpansion {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;

    public NewSkyPlaceholderExpansion(NewSky plugin, CacheHandler cacheHandler) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
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

        if (identifier.equalsIgnoreCase("newsky_island_level")) {
            return String.valueOf(cacheHandler.getIslandLevel(player.getUniqueId()));
        } else if (identifier.equalsIgnoreCase("newsky_island_uuid")) {
            return cacheHandler.getIslandUuid(player.getUniqueId()).toString();
        } else if (identifier.equalsIgnoreCase("newsky_island_owner")) {
            return cacheHandler.getIslandOwner(player.getUniqueId()).toString();
        } else if (identifier.equalsIgnoreCase("newsky_island_members")) {
            return cacheHandler.getIslandMembers(player.getUniqueId()).toString();
        } else if (identifier.equalsIgnoreCase("newsky_island_players")) {
            return cacheHandler.getIslandPlayers(player.getUniqueId()).toString();
        }

        return null;
    }
}
