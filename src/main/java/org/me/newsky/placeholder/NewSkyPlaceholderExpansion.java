package org.me.newsky.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.NewSky;

public class NewSkyPlaceholderExpansion extends PlaceholderExpansion {

    private final NewSky plugin;

    public NewSkyPlaceholderExpansion(NewSky plugin) {
        this.plugin = plugin;
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
            return "100";
        } else if (identifier.equalsIgnoreCase("newsky_island_uuid")) {
            return "100";
        } else if (identifier.equalsIgnoreCase("newsky_island_owner")) {
            return "100";
        } else if (identifier.equalsIgnoreCase("newsky_island_members")) {
            return "100";
        } else if (identifier.equalsIgnoreCase("newsky_island_players")) {
            return "100";
        }

        return null;
    }
}
