package org.me.newsky.island;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.UpgradeLevelChangedException;
import org.me.newsky.listener.IslandProtectionListener;
import org.me.newsky.listener.WorldLoadListener;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Island;
import org.me.newsky.network.IslandOperator;
import org.me.newsky.scheduler.LevelUpdateScheduler;
import org.me.newsky.snapshot.IslandSnapshot;
import org.me.newsky.thread.BukkitAsyncExecutor;
import org.me.newsky.util.IslandUtils;
import org.mockito.MockedStatic;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IslandSizeTest {
    private final UUID islandId = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final NewSky plugin = mock(NewSky.class);
    private final ConfigHandler config = mock(ConfigHandler.class);
    private final DatabaseHandler database = mock(DatabaseHandler.class);
    private final IslandSnapshot snapshots = mock(IslandSnapshot.class);
    private final World world = mock(World.class);
    private final WorldBorder border = mock(WorldBorder.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);

    @BeforeEach
    void setUp() {
        when(world.getName()).thenReturn(IslandUtils.parseIslandName(islandId));
        when(world.getWorldBorder()).thenReturn(border);
        when(scheduler.getMainThreadExecutor(plugin)).thenReturn(Runnable::run);
        BukkitAsyncExecutor async = mock(BukkitAsyncExecutor.class);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(async).execute(any());
        when(plugin.getBukkitAsyncExecutor()).thenReturn(async);
    }

    @Test
    void defaultConfigExposesAllFiveSizesAndRequirements() throws Exception {
        ConfigHandler realConfig = mock(ConfigHandler.class, CALLS_REAL_METHODS);
        try (var stream = getClass().getResourceAsStream("/upgrades.yml")) {
            assertNotNull(stream);
            Field field = ConfigHandler.class.getDeclaredField("upgrades");
            field.setAccessible(true);
            field.set(realConfig, YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
        assertTrue(realConfig.isUpgrade("island-size"));
        assertEquals(List.of(1, 2, 3, 4, 5), realConfig.getUpgradeLevels("island-size"));
        int[] sizes = {75, 100, 125, 150, 175};
        int[] requirements = {0, 200, 450, 850, 1400};
        double[] prices = {0, 60000, 180000, 450000, 1000000};
        for (int i = 0; i < sizes.length; i++) {
            assertEquals(sizes[i], realConfig.getUpgradeLimit("island-size", i + 1));
            assertEquals(requirements[i], realConfig.getUpgradeRequireLevel("island-size", i + 1));
            assertEquals(prices[i], realConfig.getUpgradePrice("island-size", i + 1));
        }
        assertEquals(5, realConfig.getUpgradeLimit("team-limit", 1));
    }

    @SuppressWarnings("UnstableApiUsage")
    @ParameterizedTest
    @ValueSource(ints = {75, 100, 125, 150, 175})
    void loadedBorderStaysAtOriginAndProtectionUsesConfiguredWidth(int size) {
        when(snapshots.get(islandId)).thenReturn(island(size));
        new WorldLoadListener(plugin, config, mock(LevelUpdateScheduler.class), snapshots).onWorldLoad(new WorldLoadEvent(world));
        verify(border).setSize(size);
        verify(border).setCenter(0.0, 0.0);

        IslandProtectionListener protection = new IslandProtectionListener(config, snapshots);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(owner);
        int min = -(size / 2);
        int max = min + size - 1;
        for (int x : new int[]{min - 1, min, max, max + 1}) {
            for (int z : new int[]{min - 1, min, max, max + 1}) {
                Block block = mock(Block.class);
                when(block.getLocation()).thenReturn(new Location(world, x, 70, z));
                BlockBreakEvent event = new BlockBreakEvent(block, player);
                protection.onBlockBreak(event);
                assertEquals(x < min || x > max || z < min || z > max, event.isCancelled(), x + "," + z);
            }
        }
    }

    @Test
    void hostRefreshesSizeAfterPurchaseAndAdminDowngrade() {
        IslandOperator operator = new IslandOperator(plugin, database, null, null, snapshots, null, "local");
        when(snapshots.reload(islandId)).thenReturn(CompletableFuture.completedFuture(null));
        when(snapshots.get(islandId)).thenReturn(island(100), island(75));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getWorld(world.getName())).thenReturn(world);
            Actor buyer = new Actor.Player(owner);
            operator.setUpgradeLevel(buyer, islandId, "island-size", 1, 2).join();
            verify(database).updateIslandUpgradeLevel(buyer, islandId, "island-size", 1, 2);
            verify(border).setSize(100);
            operator.setUpgradeLevel(new Actor.Bypass("test"), islandId, "island-size", 2, 1).join();
            verify(border).setSize(75);
            verify(border, times(2)).setCenter(0.0, 0.0);
            verify(snapshots, times(2)).reload(islandId);
        }
    }

    @Test
    void rejectedWriteDoesNotChangeTheLoadedBorder() {
        IslandOperator operator = new IslandOperator(plugin, database, null, null, snapshots, null, "local");
        Actor actor = new Actor.Player(owner);
        when(snapshots.reload(islandId)).thenReturn(CompletableFuture.completedFuture(null));
        doThrow(new UpgradeLevelChangedException()).when(database).updateIslandUpgradeLevel(actor, islandId, "island-size", 1, 2);
        CompletionException error = assertThrows(CompletionException.class, () -> operator.setUpgradeLevel(actor, islandId, "island-size", 1, 2).join());
        assertInstanceOf(UpgradeLevelChangedException.class, error.getCause());
        verify(snapshots).reload(islandId);
        verifyNoInteractions(border);
    }

    @Test
    void committedUpgradeIsNotRefundedIfSnapshotRefreshFails() {
        IslandOperator operator = new IslandOperator(plugin, database, null, null, snapshots, null, "local");
        when(snapshots.reload(islandId)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            assertDoesNotThrow(() -> operator.setUpgradeLevel(new Actor.Player(owner), islandId, "island-size", 1, 2).join());
            verify(plugin).severe(eq("Island data saved but could not refresh the snapshot: " + islandId), any(Throwable.class));
            verifyNoInteractions(border);
        }
    }

    @Test
    void committedUpgradeIsNotRefundedIfBorderRefreshFails() {
        IslandOperator operator = new IslandOperator(plugin, database, null, null, snapshots, null, "local");
        when(snapshots.reload(islandId)).thenReturn(CompletableFuture.completedFuture(null));
        when(snapshots.get(islandId)).thenReturn(island(100));
        doThrow(new IllegalStateException("border unavailable")).when(border).setSize(100);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getWorld(world.getName())).thenReturn(world);
            assertDoesNotThrow(() -> operator.setUpgradeLevel(new Actor.Player(owner), islandId, "island-size", 1, 2).join());
            verify(plugin).severe(eq("Upgrade island-size saved but could not refresh the loaded island: " + islandId), any(Throwable.class));
        }
    }

    @Test
    void otherUpgradesRefreshTheSnapshotWithoutChangingTheBorder() {
        IslandOperator operator = new IslandOperator(plugin, database, null, null, snapshots, null, "local");
        when(snapshots.reload(islandId)).thenReturn(CompletableFuture.completedFuture(null));
        operator.setUpgradeLevel(new Actor.Player(owner), islandId, "coop-limit", 1, 2).join();
        verify(snapshots).reload(islandId);
        verifyNoInteractions(border, scheduler);
    }

    @ParameterizedTest
    @ValueSource(ints = {75, 100, 125, 150, 175})
    void levelScanCountsOnlyBlocksWithinTheUpgradedSize(int size) {
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorld(world.getName())).thenReturn(world);
        when(border.getSize()).thenReturn((double) size);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(1);
        when(config.getBlockLevel("STONE")).thenReturn(100);
        when(world.getChunkAtAsync(anyInt(), anyInt(), eq(false))).thenAnswer(call -> {
            Chunk chunk = mock(Chunk.class);
            ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
            when(snapshot.getX()).thenReturn(call.getArgument(0));
            when(snapshot.getZ()).thenReturn(call.getArgument(1));
            when(snapshot.getBlockType(anyInt(), eq(0), anyInt())).thenReturn(Material.STONE);
            when(chunk.getChunkSnapshot()).thenReturn(snapshot);
            return CompletableFuture.completedFuture(chunk);
        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getWorld(world.getName())).thenReturn(world);
            assertEquals(size * size, new LevelHandler(plugin, config, database).calIslandLevel(islandId).join());
            verify(database).updateIslandLevel(islandId, size * size);
        }
    }

    private Island island(int size) {
        return new Island(islandId, false, false, owner, Set.of(), Set.of(), Set.of(), Map.of(), null, size);
    }
}
