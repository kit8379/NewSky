package org.me.newsky.island;

import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class UpgradeHandler {

    public static final String UPGRADE_TEAM_LIMIT = "team-limit";
    public static final String UPGRADE_WARPS_LIMIT = "warps-limit";
    public static final String UPGRADE_COOP_LIMIT = "coop-limit";
    public static final String UPGRADE_ISLAND_SIZE = "island-size";
    public static final String UPGRADE_GENERATOR_RATES = "generator-rates";

    private final NewSky plugin;
    private final ConfigHandler config;

    public UpgradeHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ================================================================================================================
    // Upgrade IDs
    // ================================================================================================================

    public Set<String> getUpgradeIds() {
        Set<String> ids = config.getUpgradeIds();
        return ids == null ? Collections.emptySet() : ids;
    }

    public boolean hasUpgrade(String upgradeId) {
        return getUpgradeIds().contains(upgradeId);
    }

    // ================================================================================================================
    // Levels (STRUCTURE) - generic
    // ================================================================================================================

    public Set<Integer> getLevels(String upgradeId) {
        Objects.requireNonNull(upgradeId, "upgradeId");
        Set<Integer> levels = config.getUpgradeLevels(upgradeId);
        return levels == null ? Collections.emptySet() : levels;
    }

    public int getMaxLevel(String upgradeId) {
        int max = 0;
        for (int lvl : getLevels(upgradeId)) {
            if (lvl > max) max = lvl;
        }
        return max;
    }

    public int getNextLevel(String upgradeId, int currentLevel) {
        int next = Integer.MAX_VALUE;
        for (int lvl : getLevels(upgradeId)) {
            if (lvl > currentLevel && lvl < next) next = lvl;
        }
        return (next == Integer.MAX_VALUE) ? -1 : next;
    }

    public boolean isMaxed(String upgradeId, int currentLevel) {
        int max = getMaxLevel(upgradeId);
        return max > 0 && currentLevel >= max;
    }

    // ================================================================================================================
    // Purchase gate (require-level only; economy elsewhere)
    // ================================================================================================================

    /**
     * Checks if player can purchase the NEXT level of an upgrade.
     * This method does NOT check economy, it only returns "next level + required island level + price".
     *
     * @param player       buyer (kept for API symmetry; not used after removing permission checks)
     * @param upgradeId    upgrade type
     * @param currentLevel current upgrade level for this type (from island data)
     * @param islandLevel  island level (your /is level)
     */
    public PurchaseResult canPurchaseNext(Player player, String upgradeId, int currentLevel, int islandLevel) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(upgradeId, "upgradeId");

        int next = getNextLevel(upgradeId, currentLevel);
        if (next == -1) {
            return PurchaseResult.fail(PurchaseFailReason.NO_NEXT_LEVEL);
        }

        int requireIslandLevel = config.getUpgradeRequireIslandLevel(upgradeId, next);
        if (islandLevel < requireIslandLevel) {
            return PurchaseResult.fail(PurchaseFailReason.ISLAND_LEVEL_TOO_LOW, next, requireIslandLevel);
        }

        double price = config.getUpgradePrice(upgradeId, next);
        return PurchaseResult.ok(next, requireIslandLevel, price);
    }

    // ================================================================================================================
    // Upgrade VALUE getters (per-upgrade, dedicated ConfigHandler getters)
    // ================================================================================================================

    public int getTeamLimit(int upgradeLevel) {
        int v = config.getUpgradeTeamLimit(upgradeLevel);
        return v > 0 ? v : config.getUpgradeTeamLimit(1);
    }

    public int getWarpsLimit(int upgradeLevel) {
        int v = config.getUpgradeWarpsLimit(upgradeLevel);
        return v > 0 ? v : config.getUpgradeWarpsLimit(1);
    }

    public int getCoopLimit(int upgradeLevel) {
        int v = config.getUpgradeCoopLimit(upgradeLevel);
        return v > 0 ? v : config.getUpgradeCoopLimit(1);
    }

    public int getIslandSize(int upgradeLevel) {
        int v = config.getUpgradeIslandSize(upgradeLevel);
        return v > 0 ? v : config.getUpgradeIslandSize(1);
    }

    public Map<String, Integer> getGeneratorRates(int upgradeLevel) {
        Map<String, Integer> raw = config.getUpgradeGeneratorRates(upgradeLevel);
        if (raw == null || raw.isEmpty()) {
            raw = config.getUpgradeGeneratorRates(1);
        }
        return raw == null ? Collections.emptyMap() : raw;
    }

    // ================================================================================================================
    // Types
    // ================================================================================================================

    public enum PurchaseFailReason {
        NO_NEXT_LEVEL, ISLAND_LEVEL_TOO_LOW
    }

    public record PurchaseResult(boolean ok, PurchaseFailReason failReason, int nextLevel, int requireIslandLevel,
                                 double price) {
        public static PurchaseResult ok(int nextLevel, int requireIslandLevel, double price) {
            return new PurchaseResult(true, null, nextLevel, requireIslandLevel, price);
        }

        public static PurchaseResult fail(PurchaseFailReason reason) {
            return new PurchaseResult(false, reason, -1, 0, 0.0D);
        }

        public static PurchaseResult fail(PurchaseFailReason reason, int nextLevel, int requireIslandLevel) {
            return new PurchaseResult(false, reason, nextLevel, requireIslandLevel, 0.0D);
        }
    }
}
