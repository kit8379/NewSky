package org.me.newsky.island;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.model.Actor;
import org.me.newsky.network.IslandDistributor;
import org.me.newsky.network.IslandOperator;
import org.me.newsky.snapshot.IslandSnapshot;
import org.me.newsky.thread.BukkitAsyncExecutor;
import org.me.newsky.util.IslandUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WarpHandlerTest {
    private final UUID island = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID visitor = UUID.randomUUID();
    private final Actor admin = new Actor.Bypass("test");
    private Connection connection;
    private DatabaseHandler database;
    private WarpHandler handler;
    private IslandDistributor distributor;
    private OnlinePlayerRegistry players;
    private IslandSnapshot snapshot;
    private IslandOperator operator;
    private HomeHandler homes;

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000";
        connection = DriverManager.getConnection(url);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenAnswer(ignored -> DriverManager.getConnection(url));
        NewSky plugin = mock(NewSky.class);
        database = mock(DatabaseHandler.class, CALLS_REAL_METHODS);
        field("dataSource", dataSource);
        field("prefix", "test_");
        field("plugin", plugin);
        // Use the production schema so membership and island cascade behavior is exercised.
        Method createTables = DatabaseHandler.class.getDeclaredMethod("createTables");
        createTables.setAccessible(true);
        createTables.invoke(database);
        sql("INSERT INTO test_islands (island_uuid) VALUES (?)", island);
        sql("INSERT INTO test_island_players VALUES (?, ?, 'owner')", owner, island);
        sql("INSERT INTO test_island_players VALUES (?, ?, 'member')", member, island);

        BukkitAsyncExecutor executor = mock(BukkitAsyncExecutor.class);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(executor).execute(any());
        when(plugin.getBukkitAsyncExecutor()).thenReturn(executor);
        distributor = mock(IslandDistributor.class);
        players = mock(OnlinePlayerRegistry.class);
        snapshot = new IslandSnapshot(plugin, database);
        snapshot.load(island).join();
        operator = new IslandOperator(plugin, database, null, null, snapshot, null, "test");
        when(distributor.setWarp(any(), any(), anyString(), anyString())).thenAnswer(call ->
                operator.setWarp(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        when(distributor.deleteWarp(any(), any(), anyString())).thenAnswer(call ->
                operator.deleteWarp(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(distributor.setHome(any(), any(), anyString(), anyString())).thenAnswer(call ->
                operator.setHome(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
        when(distributor.deleteHome(any(), any(), anyString())).thenAnswer(call ->
                operator.deleteHome(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        homes = new HomeHandler(plugin, database, distributor, players);
        handler = new WarpHandler(plugin, database, distributor, players);
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    @Test
    void membersShareNamesUpdateAndDeleteTheSameWarp() {
        set(owner, "Shop", 1);
        assertEquals(Set.of("shop"), handler.getWarpNames(island).join());
        set(member, "SHOP", 2);
        assertEquals(Map.of("shop", "2.0,70.0,3.0,0.0,0.0"), database.getIslandWarps(island));
        assertEquals(Set.of("shop"), handler.getWarpNames(island).join());
        handler.delWarp(new Actor.Player(owner), island, "SHOP").join();
        assertTrue(handler.getWarpNames(island).join().isEmpty());
        verify(distributor, times(2)).setWarp(any(), eq(island), eq("shop"), anyString());
        verify(distributor).deleteWarp(new Actor.Player(owner), island, "shop");
    }

    @Test
    void teleportUsesIslandUuidWithoutLookingUpPlayerMembership() {
        set(owner, "default", 1);
        when(players.isOnline(visitor)).thenReturn(true);
        when(distributor.teleportIsland(any(), any(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        handler.warp(island, "default", visitor).join();
        handler.warp(island, "DEFAULT", visitor).join();
        verify(database, never()).getIslandUuid(any());
        verify(distributor, times(2)).teleportIsland(island, visitor, IslandUtils.parseIslandName(island), "1.0,70.0,3.0,0.0,0.0");
    }

    @Test
    void warpsSurviveCreatorsDepartureButAreDeletedWithIsland() {
        set(member, "farm", 1);
        database.deleteIslandPlayer(new Actor.Player(owner), island, member);
        assertEquals(Set.of("farm"), handler.getWarpNames(island).join());
        assertThrows(IslandDoesNotExistException.class,
                () -> database.updateWarpPoint(new Actor.Player(member), island, "farm", "changed"));
        assertThrows(IslandDoesNotExistException.class,
                () -> database.deleteWarpPoint(new Actor.Player(member), island, "farm"));
        database.deleteIsland(new Actor.Player(owner), island);
        assertTrue(database.getIslandWarps(island).isEmpty());
    }

    @Test
    void coopAndVisitorsCannotModifySharedWarpsButAdminCan() throws Exception {
        set(owner, "shop", 1);
        sql("INSERT INTO test_island_coops VALUES (?, ?)", island, visitor);
        assertThrows(IslandDoesNotExistException.class,
                () -> database.updateWarpPoint(new Actor.Player(visitor), island, "shop", "changed"));
        assertThrows(IslandDoesNotExistException.class,
                () -> database.deleteWarpPoint(new Actor.Player(visitor), island, "shop"));
        assertThrows(IslandDoesNotExistException.class,
                () -> database.updateWarpPoint(new Actor.Player(UUID.randomUUID()), island, "shop", "changed"));
        handler.setWarp(admin, island, "shop", IslandUtils.parseIslandName(island), 5, 70, 3, 0, 0).join();
        assertEquals("5.0,70.0,3.0,0.0,0.0", database.getIslandWarps(island).get("shop"));
        handler.delWarp(admin, island, "shop").join();
        assertTrue(database.getIslandWarps(island).isEmpty());
    }

    @Test
    void wrongWorldAndInvalidNameDoNotWriteEvenForAdmin() {
        assertFailure(LocationNotInIslandException.class,
                handler.setWarp(admin, island, "shop", "world", 1, 2, 3, 0, 0));
        assertFailure(LocationNotInIslandException.class,
                handler.setWarp(admin, island, "shop", IslandUtils.parseIslandName(UUID.randomUUID()), 1, 2, 3, 0, 0));
        assertFailure(WarpNameNotLegalException.class,
                handler.setWarp(new Actor.Player(owner), island, "bad name", IslandUtils.parseIslandName(island), 1, 2, 3, 0, 0));
        assertTrue(database.getIslandWarps(island).isEmpty());
    }

    @Test
    void sameNameOnOtherIslandIsIndependentAndCannotBeEditedByMember() throws Exception {
        UUID otherIsland = UUID.randomUUID();
        sql("INSERT INTO test_islands (island_uuid) VALUES (?)", otherIsland);
        set(owner, "shop", 1);
        database.updateWarpPoint(admin, otherIsland, "shop", "other");
        assertThrows(IslandDoesNotExistException.class,
                () -> database.updateWarpPoint(new Actor.Player(member), otherIsland, "shop", "changed"));
        assertThrows(IslandDoesNotExistException.class,
                () -> database.deleteWarpPoint(new Actor.Player(member), otherIsland, "shop"));
        handler.delWarp(new Actor.Player(member), island, "shop").join();
        assertEquals(Map.of("shop", "other"), database.getIslandWarps(otherIsland));
        assertFailure(WarpDoesNotExistException.class, handler.delWarp(new Actor.Player(owner), island, "missing"));
    }

    @Test
    void lockedAndBannedIslandAccessRulesStillApply() throws Exception {
        set(owner, "shop", 1);
        when(players.isOnline(visitor)).thenReturn(true);
        sql("UPDATE test_islands SET `lock` = TRUE WHERE island_uuid = ?", island);
        assertFailure(IslandLockedException.class, handler.warp(island, "shop", visitor));
        sql("INSERT INTO test_island_coops VALUES (?, ?)", island, visitor);
        when(distributor.teleportIsland(any(), any(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        handler.warp(island, "shop", visitor).join();
        sql("INSERT INTO test_island_bans VALUES (?, ?)", island, visitor);
        assertFailure(PlayerBannedException.class, handler.warp(island, "shop", visitor));
        verify(distributor, times(1)).teleportIsland(any(), any(), anyString(), anyString());
    }

    @Test
    void writeWaitingOnMembershipRemovalRechecksRoleAfterLock() throws Exception {
        set(member, "shop", 1);
        connection.setAutoCommit(false);
        sql("SELECT 1 FROM test_islands WHERE island_uuid = ? FOR UPDATE", island);
        sql("DELETE FROM test_island_players WHERE player_uuid = ?", member);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch started = new CountDownLatch(1);
            Future<?> write = executor.submit(() -> {
                started.countDown();
                database.updateWarpPoint(new Actor.Player(member), island, "shop", "changed");
            });
            try {
                assertTrue(started.await(2, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> write.get(150, TimeUnit.MILLISECONDS));
            } finally {
                connection.commit();
                connection.setAutoCommit(true);
            }
            ExecutionException error = assertThrows(ExecutionException.class, () -> write.get(3, TimeUnit.SECONDS));
            assertInstanceOf(IslandDoesNotExistException.class, error.getCause());
        }
        assertEquals("1.0,70.0,3.0,0.0,0.0", database.getIslandWarps(island).get("shop"));
    }

    @Test
    void completedHomeAndWarpWritesRefreshOnlyDefaultRespawnPoints() {
        homes.setHome(owner, "DEFAULT", IslandUtils.parseIslandName(island), 1, 70, 3, 45, 10).join();
        homes.setHome(member, "default", IslandUtils.parseIslandName(island), 2, 70, 3, 0, 0).join();
        homes.setHome(owner, "mine", IslandUtils.parseIslandName(island), 99, 70, 3, 0, 0).join();
        set(owner, "default", 4);
        set(member, "shop", 99);

        assertEquals(Map.of(owner, "1.0,70.0,3.0,45.0,10.0", member, "2.0,70.0,3.0,0.0,0.0"), snapshot.get(island).getDefaultHomes());
        assertEquals("4.0,70.0,3.0,0.0,0.0", snapshot.get(island).getDefaultWarp());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.get(island).getDefaultHomes().clear());

        homes.setHome(member, "default", IslandUtils.parseIslandName(island), 5, 70, 3, 0, 0).join();
        set(member, "default", 6);
        assertEquals("5.0,70.0,3.0,0.0,0.0", snapshot.get(island).getDefaultHomes().get(member));
        assertEquals("6.0,70.0,3.0,0.0,0.0", snapshot.get(island).getDefaultWarp());

        handler.delWarp(new Actor.Player(member), island, "default").join();
        assertNull(snapshot.get(island).getDefaultWarp());
        homes.delHome(owner, "mine").join();
        snapshot.unload(island);
        snapshot.load(island).join();
        assertEquals("5.0,70.0,3.0,0.0,0.0", snapshot.get(island).getDefaultHomes().get(member));
        assertNull(snapshot.get(island).getDefaultWarp());
    }

    @Test
    void newMembersDefaultHomeIsIncludedByMembershipSnapshotReload() {
        homes.setHome(owner, "default", IslandUtils.parseIslandName(island), 1, 70, 3, 0, 0).join();
        UUID newMember = UUID.randomUUID();
        operator.addMember(island, newMember, "member").join();
        assertEquals("1.0,70.0,3.0,0.0,0.0", snapshot.get(island).getDefaultHomes().get(newMember));
        assertTrue(snapshot.get(island).getMembers().contains(newMember));
    }

    private void set(UUID player, String name, double x) {
        handler.setWarp(new Actor.Player(player), island, name, IslandUtils.parseIslandName(island), x, 70, 3, 0, 0).join();
    }

    private void assertFailure(Class<? extends Throwable> type, CompletableFuture<?> result) {
        assertInstanceOf(type, assertThrows(CompletionException.class, result::join).getCause());
    }

    private void sql(String sql, UUID... args) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setString(i + 1, args[i].toString());
            statement.execute();
        }
    }

    private void field(String name, Object value) throws Exception {
        Field field = DatabaseHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(database, value);
    }
}
