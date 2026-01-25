package org.me.newsky.island;

import org.bukkit.Material;
import org.me.newsky.NewSky;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cached generator-rates handler.
 * <p>
 * - Build cache once on enable/reload
 * - Hot path does NOT touch YAML and does NOT allocate Maps
 */
public class CobblestoneGeneratorHandler {

    private final NewSky plugin;
    private final UpgradeHandler upgradeHandler;

    private volatile Map<Integer, WeightedTable> tables = Map.of();

    private static final WeightedTable DEFAULT_TABLE = WeightedTable.ofSingle();

    public CobblestoneGeneratorHandler(NewSky plugin, UpgradeHandler upgradeHandler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.upgradeHandler = Objects.requireNonNull(upgradeHandler, "upgradeHandler");
    }

    /**
     * Rebuild all cached weighted tables from upgrades.yml (generator-rates).
     * Call this on enable and on config reload.
     */
    public void start() {
        Map<Integer, WeightedTable> built = new HashMap<>();

        // Always build base (level 1)
        WeightedTable level1 = buildTableForLevel(1);
        if (level1 == null) level1 = DEFAULT_TABLE;
        built.put(1, level1);

        // Build all other defined levels for generator-rates
        Set<Integer> levels = upgradeHandler.getLevels(UpgradeHandler.UPGRADE_GENERATOR_RATES);
        for (int lvl : levels) {
            if (lvl <= 1) continue;
            WeightedTable t = buildTableForLevel(lvl);
            built.put(lvl, t == null ? level1 : t);
        }

        this.tables = Collections.unmodifiableMap(built);
        plugin.getLogger().info("[CobblestoneGenerator] Levels=" + this.tables.keySet());
    }

    public Material roll(int upgradeLevel) {
        int lvl = upgradeLevel <= 0 ? 1 : upgradeLevel;

        WeightedTable table = tables.get(lvl);
        if (table == null) {
            table = tables.getOrDefault(1, DEFAULT_TABLE);
        }

        return table.roll(ThreadLocalRandom.current());
    }

    private WeightedTable buildTableForLevel(int level) {
        Map<String, Integer> raw = upgradeHandler.getGeneratorRates(level);

        if (raw.isEmpty()) {
            return (level == 1) ? DEFAULT_TABLE : null;
        }

        List<Material> mats = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;

        for (Map.Entry<String, Integer> e : raw.entrySet()) {
            String key = e.getKey();
            Integer wObj = e.getValue();
            if (key == null || wObj == null) continue;

            int w = wObj;
            if (w <= 0) continue;

            Material m = Material.matchMaterial(key);
            if (m == null) {
                plugin.getLogger().warning("[CobblestoneGenerator] Unknown material in generator-rates level " + level + ": " + key);
                continue;
            }

            mats.add(m);
            weights.add(w);
            total += w;
        }

        if (mats.isEmpty() || total <= 0) {
            return (level == 1) ? DEFAULT_TABLE : null;
        }

        return new WeightedTable(mats.toArray(new Material[0]), toCumulative(weights), total);
    }

    private static int[] toCumulative(List<Integer> weights) {
        int[] cum = new int[weights.size()];
        int running = 0;
        for (int i = 0; i < weights.size(); i++) {
            running += weights.get(i);
            cum[i] = running;
        }
        return cum;
    }

    private static final class WeightedTable {
        private final Material[] mats;
        private final int[] cumulative;
        private final int total;

        private WeightedTable(Material[] mats, int[] cumulative, int total) {
            this.mats = mats;
            this.cumulative = cumulative;
            this.total = total;
        }

        static WeightedTable ofSingle() {
            return new WeightedTable(new Material[]{Material.COBBLESTONE}, new int[]{100}, 100);
        }

        Material roll(Random random) {
            if (mats.length == 0 || total <= 0) {
                return Material.COBBLESTONE;
            }

            int r = random.nextInt(total);
            for (int i = 0; i < cumulative.length; i++) {
                if (r < cumulative[i]) {
                    return mats[i];
                }
            }
            return Material.COBBLESTONE;
        }
    }
}
