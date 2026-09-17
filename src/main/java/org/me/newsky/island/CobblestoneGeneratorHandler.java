package org.me.newsky.island;

import org.bukkit.Material;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weighted block tables of the generator-rates upgrade, one per configured level. Built from
 * the config at startup and on reload, so the block-form hot path never touches YAML and
 * never allocates. A level whose rates are unusable is a config error and fails startup.
 */
public class CobblestoneGeneratorHandler {


    private final NewSky plugin;
    private final ConfigHandler config;

    private volatile Map<Integer, WeightedTable> tables = Map.of();

    public CobblestoneGeneratorHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
        startup();
    }

    public void startup() {
        Map<Integer, WeightedTable> built = new HashMap<>();
        for (int level : config.getUpgradeLevels("generator-rates")) {
            built.put(level, WeightedTable.of(level, config.getUpgradeGeneratorRates(level)));
        }

        this.tables = Map.copyOf(built);
        plugin.debug("CobblestoneGeneratorHandler", "Loaded generator rates for levels " + tables.keySet());
    }

    /** The block a cobblestone generator forms on an island at that upgrade level. */
    public Material roll(int level) {
        return roll(level, ThreadLocalRandom.current().nextDouble());
    }

    /** {@code roll} is in [0, 1); each material owns the slice of that range its weight buys. */
    Material roll(int level, double roll) {
        WeightedTable table = tables.get(level);
        if (table == null) {
            throw new IllegalStateException("No generator rates configured for upgrade level " + level);
        }

        return table.pick(roll);
    }

    /** Materials with the running share of the total weight at which each one ends. */
    private record WeightedTable(Material[] materials, double[] cumulativeShares) {

        static WeightedTable of(int level, Map<String, Double> rates) {
            List<Material> materials = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (Map.Entry<String, Double> rate : rates.entrySet()) {
                Material material = Material.matchMaterial(rate.getKey());
                if (material == null) {
                    throw new IllegalStateException("Generator rates level " + level + ": '" + rate.getKey() + "' is not a material");
                }

                if (rate.getValue() <= 0) {
                    throw new IllegalStateException("Generator rates level " + level + ": weight of " + rate.getKey() + " must be positive");
                }

                materials.add(material);
                weights.add(rate.getValue());
            }

            if (materials.isEmpty()) {
                throw new IllegalStateException("Generator rates level " + level + " has no entries");
            }

            double total = weights.stream().mapToDouble(Double::doubleValue).sum();
            double[] cumulativeShares = new double[weights.size()];
            double running = 0;
            for (int i = 0; i < cumulativeShares.length; i++) {
                running += weights.get(i);
                cumulativeShares[i] = running / total;
            }

            return new WeightedTable(materials.toArray(new Material[0]), cumulativeShares);
        }

        Material pick(double roll) {
            for (int i = 0; i < cumulativeShares.length; i++) {
                if (roll < cumulativeShares[i]) {
                    return materials[i];
                }
            }

            return materials[materials.length - 1];
        }
    }
}
