package org.me.newsky.listener;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.me.newsky.island.CobblestoneGeneratorHandler;
import org.me.newsky.util.IslandUtils;

import java.util.Objects;

public final class CobblestoneGeneratorListener implements Listener {

    @FunctionalInterface
    public interface GeneratorLevelResolver {
        /**
         * Resolve the generator-rates upgrade level for the island in this world.
         * Return <= 0 to treat as level 1.
         */
        int resolveGeneratorUpgradeLevel(World world);
    }

    private final CobblestoneGeneratorHandler generatorHandler;
    private final GeneratorLevelResolver levelResolver;

    public CobblestoneGeneratorListener(CobblestoneGeneratorHandler generatorHandler, GeneratorLevelResolver levelResolver) {
        this.generatorHandler = Objects.requireNonNull(generatorHandler, "generatorHandler");
        this.levelResolver = Objects.requireNonNull(levelResolver, "levelResolver");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();

        // Only in island world(s)
        if (!IslandUtils.isIslandWorld(world.getName())) {
            return;
        }

        // Only act on cobblestone generation
        if (event.getNewState().getType() != Material.COBBLESTONE) {
            return;
        }

        int genLevel = levelResolver.resolveGeneratorUpgradeLevel(world);

        Material result = generatorHandler.roll(genLevel);
        event.getNewState().setType(result);
    }
}