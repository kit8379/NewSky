package org.me.newsky.island;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.BlockFormEvent;
import org.junit.jupiter.api.Test;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.listener.CobblestoneGeneratorListener;
import org.me.newsky.model.Island;
import org.me.newsky.snapshot.IslandSnapshot;
import org.me.newsky.util.IslandUtils;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CobblestoneGeneratorTest {
    private final NewSky plugin = mock(NewSky.class);

    private static ConfigHandler defaultUpgrades() throws Exception {
        ConfigHandler config = mock(ConfigHandler.class, CALLS_REAL_METHODS);
        try (var stream = CobblestoneGeneratorTest.class.getResourceAsStream("/upgrades.yml")) {
            assertNotNull(stream);
            Field field = ConfigHandler.class.getDeclaredField("upgrades");
            field.setAccessible(true);
            field.set(config, YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
        return config;
    }

    @Test
    void defaultConfigExposesTheRates() throws Exception {
        ConfigHandler config = defaultUpgrades();
        assertTrue(config.isUpgrade("generator-rates"));
        assertEquals(List.of(1, 2, 3, 4, 5), config.getUpgradeLevels("generator-rates"));
        assertEquals(Map.of("COBBLESTONE", 100.0), config.getUpgradeGeneratorRates(1));
        Map<String, Double> level3 = config.getUpgradeGeneratorRates(3);
        assertEquals(List.of("COBBLESTONE", "IRON_ORE", "DIAMOND_ORE"), List.copyOf(level3.keySet()));
        assertEquals(0.5, level3.get("DIAMOND_ORE"));
        assertEquals(250, config.getUpgradeRequireLevel("generator-rates", 2));
        assertEquals(80000.0, config.getUpgradePrice("generator-rates", 2));
        assertEquals("COBBLESTONE 91%, IRON_ORE 8.5%, DIAMOND_ORE 0.5%", config.getUpgradeValue("generator-rates", 3));
        assertEquals("COBBLESTONE 100%", config.getUpgradeValue("generator-rates", 1));
    }

    @Test
    void rollsFollowTheConfiguredWeights() throws Exception {
        CobblestoneGeneratorHandler generator = new CobblestoneGeneratorHandler(plugin, defaultUpgrades());
        for (double roll : new double[]{0.0, 0.5, 0.999}) {
            assertEquals(Material.COBBLESTONE, generator.roll(1, roll));
        }
        assertEquals(Material.COBBLESTONE, generator.roll(2, 0.9499));
        assertEquals(Material.IRON_ORE, generator.roll(2, 0.95));
        assertEquals(Material.IRON_ORE, generator.roll(2, 0.999));
        assertEquals(Material.COBBLESTONE, generator.roll(3, 0.9099));
        assertEquals(Material.IRON_ORE, generator.roll(3, 0.91));
        assertEquals(Material.IRON_ORE, generator.roll(3, 0.9949));
        assertEquals(Material.DIAMOND_ORE, generator.roll(3, 0.995));
        assertNotNull(generator.roll(5));
    }

    @Test
    void unknownLevelAndBadConfigFailLoudly() throws Exception {
        CobblestoneGeneratorHandler generator = new CobblestoneGeneratorHandler(plugin, defaultUpgrades());
        assertThrows(IllegalStateException.class, () -> generator.roll(6, 0.5));

        ConfigHandler config = mock(ConfigHandler.class);
        when(config.getUpgradeLevels("generator-rates")).thenReturn(List.of(1));
        for (Map<String, Double> rates : List.of(Map.of("NOT_A_MATERIAL", 1.0), Map.of("COBBLESTONE", 0.0), Map.<String, Double>of())) {
            when(config.getUpgradeGeneratorRates(1)).thenReturn(rates);
            assertThrows(IllegalStateException.class, () -> new CobblestoneGeneratorHandler(plugin, config), rates.toString());
        }
    }

    @Test
    void formedCobblestoneBecomesTheRolledBlockOnlyOnIslands() {
        UUID islandUuid = UUID.randomUUID();
        IslandSnapshot snapshots = mock(IslandSnapshot.class);
        when(snapshots.get(islandUuid)).thenReturn(new Island(islandUuid, false, false, UUID.randomUUID(), Set.of(), Set.of(), Set.of(), Map.of(), null, 1, 3));
        CobblestoneGeneratorHandler generator = mock(CobblestoneGeneratorHandler.class);
        when(generator.roll(3)).thenReturn(Material.IRON_ORE);
        CobblestoneGeneratorListener listener = new CobblestoneGeneratorListener(snapshots, generator);

        verify(formEvent(listener, IslandUtils.parseIslandName(islandUuid), Material.COBBLESTONE)).setType(Material.IRON_ORE);
        verify(formEvent(listener, "world", Material.COBBLESTONE), never()).setType(any());
        verify(formEvent(listener, IslandUtils.parseIslandName(islandUuid), Material.OBSIDIAN), never()).setType(any());
        verify(formEvent(listener, IslandUtils.parseIslandName(UUID.randomUUID()), Material.COBBLESTONE), never()).setType(any());

        when(generator.roll(3)).thenReturn(Material.COBBLESTONE);
        verify(formEvent(listener, IslandUtils.parseIslandName(islandUuid), Material.COBBLESTONE), never()).setType(any());
    }

    @SuppressWarnings("UnstableApiUsage")
    private static BlockState formEvent(CobblestoneGeneratorListener listener, String worldName, Material type) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        BlockState state = mock(BlockState.class);
        when(state.getType()).thenReturn(type);
        listener.onBlockForm(new BlockFormEvent(block, state));
        return state;
    }
}
