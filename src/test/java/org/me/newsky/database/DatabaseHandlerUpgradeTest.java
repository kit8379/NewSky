package org.me.newsky.database;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.model.Actor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseHandlerUpgradeTest {
    private final UUID island = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final Actor admin = new Actor.Bypass("test");
    private Connection connection;
    private DatabaseHandler database;

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000";
        connection = DriverManager.getConnection(url);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenAnswer(ignored -> DriverManager.getConnection(url));
        // Every upgrade has two levels: the base level allows one, the next allows two.
        ConfigHandler config = mock(ConfigHandler.class);
        when(config.getUpgradeLevels(anyString())).thenReturn(List.of(1, 2));
        when(config.getUpgradeLimit(anyString(), eq(1))).thenReturn(1);
        when(config.getUpgradeLimit(anyString(), eq(2))).thenReturn(2);
        database = mock(DatabaseHandler.class, CALLS_REAL_METHODS);
        field("dataSource", dataSource);
        field("prefix", "test_");
        field("plugin", mock(NewSky.class));
        field("config", config);
        Method createTables = DatabaseHandler.class.getDeclaredMethod("createTables");
        createTables.setAccessible(true);
        createTables.invoke(database);
        sql("INSERT INTO test_islands (island_uuid) VALUES (?)", island);
        sql("INSERT INTO test_island_players VALUES (?, ?, 'owner')", owner, island);
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    @Test
    void missingRowIsTheBaseLevelAndTheWriteIsCompareAndSet() {
        assertEquals(1, database.getIslandUpgradeLevel(island, "coop-limit"));

        database.updateIslandUpgradeLevel(admin, island, "coop-limit", 1, 2);
        assertEquals(2, database.getIslandUpgradeLevel(island, "coop-limit"));
        assertEquals(1, database.getIslandUpgradeLevel(island, "warp-limit"));

        assertThrows(UpgradeLevelChangedException.class, () -> database.updateIslandUpgradeLevel(admin, island, "coop-limit", 1, 2));
        assertEquals(2, database.getIslandUpgradeLevel(island, "coop-limit"));

        assertThrows(IslandDoesNotExistException.class, () -> database.updateIslandUpgradeLevel(admin, UUID.randomUUID(), "coop-limit", 1, 2));
        assertThrows(IslandDoesNotExistException.class, () -> database.updateIslandUpgradeLevel(new Actor.Player(UUID.randomUUID()), island, "warp-limit", 1, 2));
        database.updateIslandUpgradeLevel(new Actor.Player(owner), island, "warp-limit", 1, 2);
        assertEquals(2, database.getIslandUpgradeLevel(island, "warp-limit"));
    }

    @Test
    void coopLimitCountsOtherCoopsAndLiftsWithTheLevel() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        database.updateCoopPlayer(new Actor.Player(owner), island, first);
        assertEquals("1", assertThrows(CoopLimitReachedException.class, () -> database.updateCoopPlayer(new Actor.Player(owner), island, second)).getMessage());
        assertThrows(PlayerAlreadyCoopedException.class, () -> database.updateCoopPlayer(new Actor.Player(owner), island, first));

        database.updateIslandUpgradeLevel(admin, island, "coop-limit", 1, 2);
        database.updateCoopPlayer(new Actor.Player(owner), island, second);
        assertEquals(2, database.getIslandCoops(island).size());
    }

    @Test
    void teamLimitCountsTheOwner() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals("1", assertThrows(TeamLimitReachedException.class, () -> database.addIslandPlayer(new Actor.Player(first), island, first, "member")).getMessage());

        database.updateIslandUpgradeLevel(admin, island, "team-limit", 1, 2);
        database.addIslandPlayer(new Actor.Player(first), island, first, "member");
        assertEquals("2", assertThrows(TeamLimitReachedException.class, () -> database.addIslandPlayer(new Actor.Player(second), island, second, "member")).getMessage());
        assertEquals(2, database.getIslandPlayers(island).size());
    }

    @Test
    void homeLimitIsPerMemberAndIgnoresMovingAnExistingHome() {
        database.updateHomePoint(new Actor.Player(owner), island, owner, "default", "1,2,3,0,0");
        database.updateHomePoint(new Actor.Player(owner), island, owner, "default", "4,5,6,0,0");
        assertEquals("1", assertThrows(HomeLimitReachedException.class, () -> database.updateHomePoint(new Actor.Player(owner), island, owner, "farm", "0,0,0,0,0")).getMessage());
        assertEquals("4,5,6,0,0", database.getIslandHomes(island, owner).get("default"));

        // Another member's homes are their own count: the seeded default fills their base limit.
        UUID member = UUID.randomUUID();
        database.updateIslandUpgradeLevel(admin, island, "team-limit", 1, 2);
        database.addIslandPlayer(new Actor.Player(member), island, member, "member");
        assertThrows(HomeLimitReachedException.class, () -> database.updateHomePoint(new Actor.Player(member), island, member, "farm", "0,0,0,0,0"));

        database.updateIslandUpgradeLevel(admin, island, "home-limit", 1, 2);
        database.updateHomePoint(new Actor.Player(owner), island, owner, "farm", "0,0,0,0,0");
        database.updateHomePoint(new Actor.Player(member), island, member, "farm", "0,0,0,0,0");
        assertEquals(2, database.getIslandHomes(island, owner).size());
        assertEquals(2, database.getIslandHomes(island, member).size());
    }

    @Test
    void warpLimitIgnoresMovingAnExistingWarp() {
        database.updateWarpPoint(new Actor.Player(owner), island, "shop", "1,2,3,0,0");
        database.updateWarpPoint(new Actor.Player(owner), island, "shop", "4,5,6,0,0");
        assertEquals("1", assertThrows(WarpLimitReachedException.class, () -> database.updateWarpPoint(new Actor.Player(owner), island, "farm", "0,0,0,0,0")).getMessage());
        assertEquals("4,5,6,0,0", database.getIslandWarps(island).get("shop"));

        database.updateIslandUpgradeLevel(admin, island, "warp-limit", 1, 2);
        database.updateWarpPoint(new Actor.Player(owner), island, "farm", "0,0,0,0,0");
        assertEquals(2, database.getIslandWarps(island).size());
    }

    @Test
    void adminCanExceedAllFourLimitsWithoutUpgradingTheIsland() {
        UUID member = UUID.randomUUID();
        database.addIslandPlayer(admin, island, member, "member");
        assertEquals(2, database.getIslandPlayers(island).size());
        assertThrows(TeamLimitReachedException.class, () -> database.addIslandPlayer(new Actor.Player(owner), island, UUID.randomUUID(), "member"));
        assertThrows(IslandPlayerAlreadyExistsException.class, () -> database.addIslandPlayer(admin, island, member, "member"));

        database.updateHomePoint(admin, island, member, "farm", "0,0,0,0,0");
        assertEquals(2, database.getIslandHomes(island, member).size());
        assertThrows(HomeLimitReachedException.class, () -> database.updateHomePoint(new Actor.Player(member), island, member, "mine", "0,0,0,0,0"));
        assertThrows(IslandDoesNotExistException.class, () -> database.updateHomePoint(admin, island, UUID.randomUUID(), "home", "0,0,0,0,0"));

        database.updateWarpPoint(admin, island, "shop", "0,0,0,0,0");
        database.updateWarpPoint(admin, island, "farm", "0,0,0,0,0");
        assertEquals(2, database.getIslandWarps(island).size());
        assertThrows(WarpLimitReachedException.class, () -> database.updateWarpPoint(new Actor.Player(owner), island, "mine", "0,0,0,0,0"));

        UUID coop = UUID.randomUUID();
        database.updateCoopPlayer(admin, island, coop);
        database.updateCoopPlayer(admin, island, UUID.randomUUID());
        assertEquals(2, database.getIslandCoops(island).size());
        assertThrows(CoopLimitReachedException.class, () -> database.updateCoopPlayer(new Actor.Player(owner), island, UUID.randomUUID()));
        assertThrows(PlayerAlreadyCoopedException.class, () -> database.updateCoopPlayer(admin, island, coop));
        assertThrows(CannotCoopIslandPlayerException.class, () -> database.updateCoopPlayer(admin, island, member));

        for (String upgrade : List.of("team-limit", "home-limit", "warp-limit", "coop-limit")) {
            assertEquals(1, database.getIslandUpgradeLevel(island, upgrade));
        }
    }

    private void sql(String statement, Object... values) throws Exception {
        try (PreparedStatement prepared = connection.prepareStatement(statement)) {
            for (int i = 0; i < values.length; i++) {
                prepared.setString(i + 1, values[i].toString());
            }
            prepared.executeUpdate();
        }
    }

    private void field(String name, Object value) throws Exception {
        Field field = DatabaseHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(database, value);
    }
}
