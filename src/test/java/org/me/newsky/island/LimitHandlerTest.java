package org.me.newsky.island;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.listener.IslandLimitListener;
import org.me.newsky.util.IslandUtils;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LimitHandlerTest {
    private final NewSky plugin = mock(NewSky.class);
    private final UUID island = UUID.randomUUID();

    private static ConfigHandler defaultLimits() throws Exception {
        ConfigHandler config = mock(ConfigHandler.class, CALLS_REAL_METHODS);
        try (var stream = LimitHandlerTest.class.getResourceAsStream("/limits.yml")) {
            assertNotNull(stream);
            Field field = ConfigHandler.class.getDeclaredField("limits");
            field.setAccessible(true);
            field.set(config, YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
        return config;
    }

    @Test
    void defaultConfigExposesTheLimits() throws Exception {
        ConfigHandler config = defaultLimits();
        assertEquals(Map.of("HOPPER", 50, "DISPENSER", 100, "DROPPER", 100, "OBSERVER", 100, "PISTON", 500), config.getBlockLimits());
        assertEquals(List.of("HOPPER", "DISPENSER", "DROPPER", "OBSERVER", "PISTON"), List.copyOf(config.getBlockLimits().keySet()));
        assertEquals(Map.of("MINECART", 20, "ITEM_FRAME", 100, "ARMOR_STAND", 50), config.getEntityLimits());
    }

    @Test
    void unknownNamesFailStartup() {
        ConfigHandler config = mock(ConfigHandler.class);
        when(config.getBlockLimits()).thenReturn(Map.of("NOT_A_MATERIAL", 1));
        when(config.getEntityLimits()).thenReturn(Map.of());
        assertThrows(IllegalStateException.class, () -> new LimitHandler(plugin, config));

        when(config.getBlockLimits()).thenReturn(Map.of());
        when(config.getEntityLimits()).thenReturn(Map.of("BOAT", 1));
        assertThrows(IllegalStateException.class, () -> new LimitHandler(plugin, config));

        when(config.getBlockLimits()).thenReturn(Map.of("HOPPER", -1));
        when(config.getEntityLimits()).thenReturn(Map.of());
        assertThrows(IllegalStateException.class, () -> new LimitHandler(plugin, config));

        when(config.getBlockLimits()).thenReturn(Map.of());
        when(config.getEntityLimits()).thenReturn(Map.of("MINECART", -1));
        assertThrows(IllegalStateException.class, () -> new LimitHandler(plugin, config));
    }

    @Test
    void zeroForbidsOutrightAndUnlistedIsUnlimited() {
        ConfigHandler config = mock(ConfigHandler.class);
        when(config.getBlockLimits()).thenReturn(Map.of("HOPPER", 0));
        when(config.getEntityLimits()).thenReturn(Map.of("MINECART", 0));
        LimitHandler limits = new LimitHandler(plugin, config);

        assertEquals(0, limits.getBlockLimit(Material.HOPPER));
        assertEquals(LimitHandler.UNLIMITED, limits.getBlockLimit(Material.DISPENSER));
        limits.reset(island, new int[Material.values().length]);
        assertFalse(limits.tryIncrement(island, Material.HOPPER));
        assertTrue(limits.tryIncrement(island, Material.DISPENSER));

        World world = mock(World.class);
        when(world.getEntities()).thenReturn(List.of());
        assertEquals(0, limits.getEntityLimit(EntityType.MINECART));
        assertFalse(limits.canSpawnEntity(world, EntityType.MINECART));
        assertTrue(limits.canSpawnEntity(world, EntityType.ARMOR_STAND));
    }

    @Test
    void blocksAreDeniedUntilTheFirstScanAndAtTheCap() throws Exception {
        LimitHandler limits = new LimitHandler(plugin, defaultLimits());
        assertEquals(50, limits.getBlockLimit(Material.HOPPER));
        assertEquals(LimitHandler.UNLIMITED, limits.getBlockLimit(Material.STONE));

        assertFalse(limits.isReady(island));
        assertTrue(limits.tryIncrement(island, Material.STONE));
        assertFalse(limits.tryIncrement(island, Material.HOPPER));

        int[] counts = new int[Material.values().length];
        counts[Material.HOPPER.ordinal()] = 49;
        limits.reset(island, counts);
        assertTrue(limits.isReady(island));
        assertTrue(limits.tryIncrement(island, Material.HOPPER));
        assertFalse(limits.tryIncrement(island, Material.HOPPER));
        assertEquals(50, counts[Material.HOPPER.ordinal()]);

        limits.decrement(island, Material.HOPPER);
        assertTrue(limits.tryIncrement(island, Material.HOPPER));
        assertFalse(limits.tryIncrement(island, Material.HOPPER));

        limits.unload(island);
        assertFalse(limits.isReady(island));
        assertFalse(limits.tryIncrement(island, Material.HOPPER));
    }

    @Test
    void decrementNeverGoesBelowZero() throws Exception {
        LimitHandler limits = new LimitHandler(plugin, defaultLimits());
        limits.reset(island, new int[Material.values().length]);
        limits.decrement(island, Material.HOPPER);
        for (int i = 0; i < 50; i++) {
            assertTrue(limits.tryIncrement(island, Material.HOPPER), "hopper " + (i + 1));
        }
        assertFalse(limits.tryIncrement(island, Material.HOPPER));
    }

    @Test
    void entityLimitCountsLiveEntitiesOfThatType() throws Exception {
        LimitHandler limits = new LimitHandler(plugin, defaultLimits());
        World world = mock(World.class);

        List<Entity> entities = new ArrayList<>(entities(EntityType.MINECART, 19));
        entities.addAll(entities(EntityType.ARMOR_STAND, 5));
        when(world.getEntities()).thenReturn(entities);
        assertTrue(limits.canSpawnEntity(world, EntityType.MINECART));

        List<Entity> full = entities(EntityType.MINECART, 20);
        when(world.getEntities()).thenReturn(full);
        assertFalse(limits.canSpawnEntity(world, EntityType.MINECART));

        assertTrue(limits.canSpawnEntity(world, EntityType.ZOMBIE));
        verify(world, times(2)).getEntities();
    }

    @Test
    void placeIsDeniedWithAMessageOnlyOnIslands() {
        LimitHandler limits = mock(LimitHandler.class);
        when(limits.getBlockLimit(any())).thenReturn(LimitHandler.UNLIMITED);
        when(limits.getBlockLimit(Material.HOPPER)).thenReturn(50);
        ConfigHandler config = mock(ConfigHandler.class);
        Component full = Component.text("full");
        Component calculating = Component.text("calculating");
        when(config.getBlockLimitReachedMessage("HOPPER", 50)).thenReturn(full);
        when(config.getBlockLimitCalculatingMessage()).thenReturn(calculating);
        IslandLimitListener listener = new IslandLimitListener(plugin, config, limits);
        Player player = mock(Player.class);
        String islandWorld = IslandUtils.parseIslandName(island);

        BlockPlaceEvent notReady = placeEvent(islandWorld, Material.HOPPER, player);
        listener.onBlockPlace(notReady);
        assertTrue(notReady.isCancelled());
        verify(player).sendMessage(calculating);
        verify(limits, never()).tryIncrement(any(), any());

        when(limits.isReady(island)).thenReturn(true);
        when(limits.tryIncrement(island, Material.HOPPER)).thenReturn(false);
        BlockPlaceEvent denied = placeEvent(islandWorld, Material.HOPPER, player);
        listener.onBlockPlace(denied);
        assertTrue(denied.isCancelled());
        verify(player).sendMessage(full);

        when(limits.tryIncrement(island, Material.HOPPER)).thenReturn(true);
        BlockPlaceEvent allowed = placeEvent(islandWorld, Material.HOPPER, player);
        listener.onBlockPlace(allowed);
        assertFalse(allowed.isCancelled());

        BlockPlaceEvent offIsland = placeEvent("world", Material.HOPPER, player);
        listener.onBlockPlace(offIsland);
        assertFalse(offIsland.isCancelled());

        BlockPlaceEvent untracked = placeEvent(islandWorld, Material.STONE, player);
        listener.onBlockPlace(untracked);
        assertFalse(untracked.isCancelled());

        verify(limits, times(2)).tryIncrement(any(), any());
        verify(player, times(2)).sendMessage(any(Component.class));
    }

    @Test
    void breakReleasesTrackedBlocksOnIslandsOnly() {
        LimitHandler limits = mock(LimitHandler.class);
        when(limits.getBlockLimit(any())).thenReturn(LimitHandler.UNLIMITED);
        when(limits.getBlockLimit(Material.HOPPER)).thenReturn(50);
        IslandLimitListener listener = new IslandLimitListener(plugin, mock(ConfigHandler.class), limits);
        Player player = mock(Player.class);

        listener.onBlockBreak(new BlockBreakEvent(block(IslandUtils.parseIslandName(island), Material.HOPPER), player));
        listener.onBlockBreak(new BlockBreakEvent(block(IslandUtils.parseIslandName(island), Material.STONE), player));
        listener.onBlockBreak(new BlockBreakEvent(block("world", Material.HOPPER), player));

        verify(limits).decrement(island, Material.HOPPER);
        verify(limits, times(1)).decrement(any(), any());
    }

    @Test
    void spawnsAreDeniedWhenTheWorldIsFull() {
        LimitHandler limits = mock(LimitHandler.class);
        when(limits.getEntityLimit(any())).thenReturn(LimitHandler.UNLIMITED);
        when(limits.getEntityLimit(EntityType.MINECART)).thenReturn(20);
        IslandLimitListener listener = new IslandLimitListener(plugin, mock(ConfigHandler.class), limits);
        World islandWorld = world(IslandUtils.parseIslandName(island));
        World overworld = world("world");

        when(limits.canSpawnEntity(islandWorld, EntityType.MINECART)).thenReturn(false);
        EntityPlaceEvent denied = new EntityPlaceEvent(entity(EntityType.MINECART), mock(Player.class), block(islandWorld, Material.RAIL), BlockFace.UP);
        listener.onEntityPlace(denied);
        assertTrue(denied.isCancelled());

        EntityPlaceEvent offIsland = new EntityPlaceEvent(entity(EntityType.MINECART), mock(Player.class), block(overworld, Material.RAIL), BlockFace.UP);
        listener.onEntityPlace(offIsland);
        assertFalse(offIsland.isCancelled());

        LivingEntity zombie = mock(LivingEntity.class);
        when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
        when(zombie.getLocation()).thenReturn(new Location(islandWorld, 0, 0, 0));
        CreatureSpawnEvent unlimited = new CreatureSpawnEvent(zombie, CreatureSpawnEvent.SpawnReason.NATURAL);
        listener.onCreatureSpawn(unlimited);
        assertFalse(unlimited.isCancelled());

        verify(limits, times(1)).canSpawnEntity(any(), any());
    }

    private static List<Entity> entities(EntityType type, int count) {
        List<Entity> entities = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entities.add(entity(type));
        }
        return entities;
    }

    private static Entity entity(EntityType type) {
        Entity entity = mock(Entity.class);
        when(entity.getType()).thenReturn(type);
        return entity;
    }

    private static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }

    private static Block block(String worldName, Material type) {
        return block(world(worldName), type);
    }

    private static Block block(World world, Material type) {
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getType()).thenReturn(type);
        return block;
    }

    private static BlockPlaceEvent placeEvent(String worldName, Material type, Player player) {
        return new BlockPlaceEvent(block(worldName, type), mock(BlockState.class), mock(Block.class), mock(ItemStack.class), player, true);
    }
}
