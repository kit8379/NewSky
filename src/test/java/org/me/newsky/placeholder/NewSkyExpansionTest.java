package org.me.newsky.placeholder;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.model.IslandTop;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NewSkyExpansionTest {
    private NewSky plugin;
    private NewSkyAPI api;
    private NewSkyExpansion expansion;
    private OfflinePlayer player;
    private final AtomicLong clock = new AtomicLong();
    private final UUID playerId = UUID.randomUUID();
    private final UUID islandId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID coopId = UUID.randomUUID();
    private final UUID bannedId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        plugin = mock(NewSky.class);
        api = mock(NewSkyAPI.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), eq(1200L), eq(1200L)))
                .thenReturn(mock(BukkitTask.class));
        expansion = new NewSkyExpansion(plugin, api, clock::get);
        player = player(playerId);
        when(api.getIslandUuid(playerId)).thenReturn(CompletableFuture.completedFuture(islandId));
        when(api.getIslandOwner(islandId)).thenReturn(CompletableFuture.completedFuture(ownerId));
        when(api.getIslandMembers(islandId)).thenReturn(CompletableFuture.completedFuture(Set.of(playerId)));
        when(api.getIslandCoops(islandId)).thenReturn(CompletableFuture.completedFuture(Set.of(coopId)));
        when(api.getIslandBans(islandId)).thenReturn(CompletableFuture.completedFuture(Set.of(bannedId)));
        when(api.getIslandLevel(islandId)).thenReturn(CompletableFuture.completedFuture(42));
        when(api.isIslandLock(islandId)).thenReturn(CompletableFuture.completedFuture(true));
        when(api.isIslandPvp(islandId)).thenReturn(CompletableFuture.completedFuture(false));
        when(api.getPlayerNames(anyCollection())).thenReturn(CompletableFuture.completedFuture(
                Map.of(ownerId, "Owner", playerId, "Member", coopId, "Coop")));
    }

    @Test
    void returnsAllPlayerFieldsWithCorrectMembership() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("island_level", "42"), Map.entry("island_members", "1"),
                Map.entry("island_uuid", islandId.toString()), Map.entry("island_owner", "Owner"),
                Map.entry("has_island", "true"), Map.entry("island_role", "member"),
                Map.entry("island_owner_uuid", ownerId.toString()), Map.entry("island_lock", "true"),
                Map.entry("island_pvp", "false"), Map.entry("island_players", "2"),
                Map.entry("island_members_list", "Member"), Map.entry("island_coops", "1"),
                Map.entry("island_coops_list", "Coop"), Map.entry("island_bans", "1"),
                Map.entry("island_bans_list", bannedId.toString()));
        expected.forEach((key, value) -> assertEquals(value, expansion.onRequest(player, key), key));
        assertEquals("42", expansion.onRequest(player, "ISLAND_LEVEL"));
        verify(api, times(1)).getIslandUuid(playerId);
        for (UUID uuid : List.of(ownerId, playerId, coopId, bannedId)) {
            verify(api).getPlayerNames(Set.of(uuid));
        }
        verify(api, never()).getIslandRank(any());
        verify(api, never()).getTopIslandLevels(anyInt());
    }

    @Test
    void handlesSoloOwner() {
        when(api.getIslandOwner(islandId)).thenReturn(CompletableFuture.completedFuture(playerId));
        when(api.getIslandMembers(islandId)).thenReturn(CompletableFuture.completedFuture(Set.of()));
        assertEquals("owner", expansion.onRequest(player, "island_role"));
        assertEquals("0", expansion.onRequest(player, "island_members"));
        assertEquals("1", expansion.onRequest(player, "island_players"));
        assertEquals("", expansion.onRequest(player, "island_members_list"));
    }

    @Test
    void missingIslandReturnsZeroFalseNoneAndEmptyText() {
        when(api.getIslandUuid(playerId)).thenReturn(CompletableFuture.failedFuture(new IslandDoesNotExistException()));
        for (String key : List.of("island_level", "island_members", "island_players", "island_coops",
                "island_bans", "island_rank")) {
            assertEquals("0", expansion.onRequest(player, key), key);
        }
        for (String key : List.of("has_island", "island_lock", "island_pvp")) {
            assertEquals("false", expansion.onRequest(player, key), key);
        }
        assertEquals("none", expansion.onRequest(player, "island_role"));
        for (String key : List.of("island_owner", "island_owner_uuid", "island_uuid", "island_members_list",
                "island_coops_list", "island_bans_list")) {
            assertEquals("", expansion.onRequest(player, key), key);
        }
        verify(plugin, never()).severe(anyString(), any(Throwable.class));
    }

    @Test
    void invalidNamesAndNullPlayersDoNotQueryPlayerData() {
        for (String key : List.of("unknown", "top_0_owner", "top_-1_level", "top_01_owner",
                "top_2147483648_owner", "top_1_invalid", "top_1_level_extra", "island_online",
                "player_homes", "player_homes_list", "player_warps", "player_warps_list")) {
            assertNull(expansion.onRequest(player, key), key);
        }
        assertEquals("", expansion.onRequest(null, "island_level"));
        verify(api, never()).getIslandUuid(any());
        verify(api, never()).getTopIslandLevels(anyInt());
    }

    @Test
    void concurrentRequestsDoNotBlockOrDuplicateAnInFlightLoad() throws Exception {
        CompletableFuture<UUID> loading = new CompletableFuture<>();
        when(api.getIslandUuid(playerId)).thenReturn(loading);
        List<CompletableFuture<Void>> requests = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            requests.add(CompletableFuture.runAsync(() ->
                    assertEquals("", expansion.onRequest(player, "island_members_list"))));
        }
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).get(3, TimeUnit.SECONDS);
        verify(api, times(1)).getIslandUuid(playerId);
        loading.complete(islandId);
        assertEquals("Member", expansion.onRequest(player, "island_members_list"));
    }

    @Test
    void rankLoadsSeparatelyWithoutFetchingOtherIslandDetails() {
        when(api.getIslandRank(islandId)).thenReturn(CompletableFuture.completedFuture(123L));
        assertEquals("123", expansion.onRequest(player, "island_rank"));
        assertEquals("123", expansion.onRequest(player, "island_rank"));
        verify(api, times(1)).getIslandRank(islandId);
        verify(api, never()).getIslandOwner(any());
    }

    @Test
    void leaderboardWorksWithoutPlayerAndSharesRowsAndFields() {
        IslandTop first = new IslandTop(islandId, ownerId, 42, Set.of(playerId));
        IslandTop second = new IslandTop(UUID.randomUUID(), bannedId, 0, Set.of());
        when(api.getTopIslandLevels(2)).thenReturn(CompletableFuture.completedFuture(List.of(first, second)));
        assertEquals("0", expansion.onRequest(null, "top_2_level"));
        assertEquals(bannedId.toString(), expansion.onRequest(null, "top_2_owner"));
        assertEquals("Owner", expansion.onRequest(player, "top_1_owner"));
        assertEquals("1", expansion.onRequest(null, "top_1_members"));
        assertEquals(islandId.toString(), expansion.onRequest(null, "top_1_uuid"));
        verify(api, times(1)).getTopIslandLevels(2);
        verify(api, never()).getIslandUuid(any());

        when(api.getTopIslandLevels(10)).thenReturn(CompletableFuture.completedFuture(List.of(first, second)));
        assertEquals("", expansion.onRequest(null, "top_10_level"));
        assertEquals("", expansion.onRequest(null, "top_10_owner"));
        verify(api, times(1)).getTopIslandLevels(10);
    }

    @Test
    void leaderboardExpandsAfterPendingLoadWithoutDuplicatingRequests() {
        CompletableFuture<List<IslandTop>> loading = new CompletableFuture<>();
        when(api.getTopIslandLevels(1)).thenReturn(loading);
        when(api.getTopIslandLevels(10)).thenReturn(CompletableFuture.completedFuture(List.of()));
        assertEquals("", expansion.onRequest(null, "top_1_level"));
        assertEquals("", expansion.onRequest(null, "top_10_owner"));
        verify(api, never()).getTopIslandLevels(10);
        loading.complete(List.of());
        assertEquals("", expansion.onRequest(null, "top_10_level"));
        verify(api, times(1)).getTopIslandLevels(1);
        verify(api, times(1)).getTopIslandLevels(10);
    }

    @Test
    void databaseFailuresAreLoggedAndDoNotPretendThePlayerHasNoIsland() {
        RuntimeException failure = new RuntimeException("database unavailable");
        when(api.getIslandUuid(playerId)).thenReturn(CompletableFuture.failedFuture(failure));
        assertEquals("", expansion.onRequest(player, "has_island"));
        verify(plugin).severe("Failed to load placeholder data for " + playerId, failure);
        when(api.getTopIslandLevels(1)).thenReturn(CompletableFuture.failedFuture(failure));
        assertEquals("", expansion.onRequest(null, "top_1_level"));
        verify(plugin).severe(eq("Failed to load leaderboard placeholders"),
                argThat(error -> error == failure || error.getCause() == failure));
    }

    @Test
    void refreshServesPreviousValuesThenClearsDeletedIslandAndLeaderboard() {
        when(api.getTopIslandLevels(1)).thenReturn(CompletableFuture.completedFuture(
                List.of(new IslandTop(islandId, ownerId, 42, Set.of(playerId)))));
        assertEquals("42", expansion.onRequest(player, "island_level"));
        assertEquals("42", expansion.onRequest(null, "top_1_level"));
        clock.addAndGet(TimeUnit.SECONDS.toNanos(30));
        CompletableFuture<UUID> refresh = new CompletableFuture<>();
        CompletableFuture<List<IslandTop>> topRefresh = new CompletableFuture<>();
        when(api.getIslandUuid(playerId)).thenReturn(refresh);
        when(api.getTopIslandLevels(1)).thenReturn(topRefresh);
        assertEquals("42", expansion.onRequest(player, "island_level"));
        assertEquals("42", expansion.onRequest(null, "top_1_level"));
        refresh.completeExceptionally(new IslandDoesNotExistException());
        topRefresh.complete(List.of());
        assertEquals("0", expansion.onRequest(player, "island_level"));
        assertEquals("false", expansion.onRequest(player, "has_island"));
        assertEquals("", expansion.onRequest(null, "top_1_level"));
    }

    @Test
    void levelRequestsOnlyLoadMembershipAndSharedLevel() {
        OfflinePlayer other = player(UUID.randomUUID());
        when(api.getIslandUuid(other.getUniqueId())).thenReturn(CompletableFuture.completedFuture(islandId));
        assertEquals("42", expansion.onRequest(player, "island_level"));
        assertEquals("42", expansion.onRequest(other, "island_level"));
        assertEquals("42", expansion.onRequest(player, "island_level"));
        verify(api).getIslandUuid(playerId);
        verify(api).getIslandUuid(other.getUniqueId());
        verify(api, times(1)).getIslandLevel(islandId);
        verifyNoMoreInteractions(api);
    }

    @Test
    void hundredPlayersOnTwentyIslandsUse120QueriesPerLevelRefresh() {
        reset(api);
        Map<UUID, UUID> memberships = new HashMap<>();
        List<OfflinePlayer> players = new ArrayList<>();
        for (int island = 0; island < 20; island++) {
            UUID id = UUID.randomUUID();
            for (int member = 0; member < 5; member++) {
                UUID playerUuid = UUID.randomUUID();
                memberships.put(playerUuid, id);
                players.add(player(playerUuid));
            }
        }
        when(api.getIslandUuid(any())).thenAnswer(call -> CompletableFuture.completedFuture(memberships.get(call.getArgument(0))));
        when(api.getIslandLevel(any())).thenReturn(CompletableFuture.completedFuture(42));
        for (int second = 0; second < 5; second++) {
            players.forEach(online -> assertEquals("42", expansion.onRequest(online, "island_level")));
            clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
        }
        verify(api, times(100)).getIslandUuid(any());
        verify(api, times(20)).getIslandLevel(any());
        verifyNoMoreInteractions(api);
        players.forEach(online -> assertEquals("42", expansion.onRequest(online, "island_level")));
        verify(api, times(200)).getIslandUuid(any());
        verify(api, times(40)).getIslandLevel(any());
        verifyNoMoreInteractions(api);
    }

    @Test
    void countsShareSourcesAndNamesOnlyLoadWhenAListIsRequested() {
        assertEquals("1", expansion.onRequest(player, "island_members"));
        assertEquals("2", expansion.onRequest(player, "island_players"));
        verify(api).getIslandUuid(playerId);
        verify(api).getIslandMembers(islandId);
        verifyNoMoreInteractions(api);
        assertEquals("Member", expansion.onRequest(player, "island_members_list"));
        verify(api).getPlayerNames(Set.of(playerId));
        verifyNoMoreInteractions(api);
    }

    @Test
    void movingToAnotherIslandDoesNotReuseOldIslandData() {
        UUID nextIsland = UUID.randomUUID();
        assertEquals("42", expansion.onRequest(player, "island_level"));
        clock.addAndGet(TimeUnit.SECONDS.toNanos(5));
        when(api.getIslandUuid(playerId)).thenReturn(CompletableFuture.completedFuture(nextIsland));
        when(api.getIslandLevel(nextIsland)).thenReturn(CompletableFuture.completedFuture(7));
        assertEquals("7", expansion.onRequest(player, "island_level"));
    }

    @Test
    void rankIsSharedByIslandAndRankAndTopRefreshAfterThirtySeconds() {
        OfflinePlayer other = player(UUID.randomUUID());
        when(api.getIslandUuid(other.getUniqueId())).thenReturn(CompletableFuture.completedFuture(islandId));
        when(api.getIslandRank(islandId)).thenReturn(CompletableFuture.completedFuture(1L));
        when(api.getTopIslandLevels(1)).thenReturn(CompletableFuture.completedFuture(
                List.of(new IslandTop(islandId, ownerId, 42, Set.of(playerId)))));
        assertEquals("1", expansion.onRequest(player, "island_rank"));
        assertEquals("1", expansion.onRequest(other, "island_rank"));
        assertEquals("42", expansion.onRequest(player, "island_level"));
        assertEquals("42", expansion.onRequest(null, "top_1_level"));

        clock.addAndGet(TimeUnit.SECONDS.toNanos(5));
        when(api.getIslandLevel(islandId)).thenReturn(CompletableFuture.completedFuture(43));
        when(api.getIslandRank(islandId)).thenReturn(CompletableFuture.completedFuture(2L));
        when(api.getTopIslandLevels(1)).thenReturn(CompletableFuture.completedFuture(List.of()));
        assertEquals("43", expansion.onRequest(player, "island_level"));
        assertEquals("1", expansion.onRequest(player, "island_rank"));
        assertEquals("42", expansion.onRequest(null, "top_1_level"));
        clock.addAndGet(TimeUnit.SECONDS.toNanos(24));
        assertEquals("1", expansion.onRequest(other, "island_rank"));
        assertEquals("42", expansion.onRequest(null, "top_1_level"));
        verify(api, times(1)).getIslandRank(islandId);
        verify(api, times(1)).getTopIslandLevels(1);

        clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertEquals("2", expansion.onRequest(other, "island_rank"));
        assertEquals("", expansion.onRequest(null, "top_1_level"));
        verify(api, times(2)).getIslandRank(islandId);
        verify(api, times(2)).getTopIslandLevels(1);
    }

    @Test
    void aSharedPendingLevelLoadDoesNotDuplicateOrBlock() throws Exception {
        CompletableFuture<Integer> loading = new CompletableFuture<>();
        when(api.getIslandLevel(islandId)).thenReturn(loading);
        List<OfflinePlayer> players = new ArrayList<>();
        List<CompletableFuture<Void>> requests = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID id = UUID.randomUUID();
            when(api.getIslandUuid(id)).thenReturn(CompletableFuture.completedFuture(islandId));
            players.add(player(id));
        }
        for (OfflinePlayer other : players) {
            requests.add(CompletableFuture.runAsync(() -> assertEquals("", expansion.onRequest(other, "island_level"))));
        }
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).get(3, TimeUnit.SECONDS);
        verify(api, times(1)).getIslandLevel(islandId);
        loading.complete(42);
        assertEquals("42", expansion.onRequest(player, "island_level"));
    }

    private OfflinePlayer player(UUID uuid) {
        OfflinePlayer result = mock(OfflinePlayer.class);
        when(result.getUniqueId()).thenReturn(uuid);
        return result;
    }
}
