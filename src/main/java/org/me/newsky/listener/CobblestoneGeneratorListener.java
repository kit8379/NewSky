package org.me.newsky.listener;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.me.newsky.NewSky;
import org.me.newsky.island.CobblestoneGeneratorHandler;
import org.me.newsky.island.UpgradeHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public final class CobblestoneGeneratorListener implements Listener {

    private final NewSky plugin;
    private final CobblestoneGeneratorHandler generatorHandler;

    public CobblestoneGeneratorListener(NewSky plugin, CobblestoneGeneratorHandler generatorHandler) {
        this.plugin = plugin;
        this.generatorHandler = generatorHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();

        // Only in island world(s)
        if (!IslandUtils.isIslandWorld(world.getName())) {
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(world.getName());

        // Only act on cobblestone generator
        if (event.getNewState().getType() != Material.COBBLESTONE) {
            return;
        }

        int genLevel = plugin.getApi().getCurrentUpgradeLevel(islandUuid, UpgradeHandler.UPGRADE_GENERATOR_RATES);

        Material result = generatorHandler.roll(genLevel);
        event.getNewState().setType(result);
    }
}