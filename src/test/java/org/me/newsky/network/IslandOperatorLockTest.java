package org.me.newsky.network;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Island;
import org.me.newsky.snapshot.IslandSnapshot;
import org.me.newsky.world.WorldHandler;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IslandOperatorLockTest {
    private final UUID island = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID coop = UUID.randomUUID();
    private final UUID visitor = UUID.randomUUID();
    private final Actor actor = new Actor.Player(owner);
    private final NewSky plugin = mock(NewSky.class);
    private final DatabaseHandler database = mock(DatabaseHandler.class);
    private final IslandSnapshot snapshots = mock(IslandSnapshot.class);
    private final WorldHandler worlds = mock(WorldHandler.class);
    private final IslandOperator operator = new IslandOperator(plugin, null, database, worlds, null, snapshots, null, "local");

    IslandOperatorLockTest() {
        when(database.toggleIslandLock(actor, island)).thenReturn(true);
        when(snapshots.reload(island)).thenReturn(CompletableFuture.completedFuture(null));
        when(worlds.removePlayersFromWorld(eq("island-" + island), any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void lockingEvictsByTheReloadedSnapshotWithoutReadingTheDatabase() {
        when(snapshots.get(island)).thenReturn(new Island(island, true, false, owner, Set.of(member), Set.of(coop), Set.of(), Map.of(), null, 1, 1));

        assertTrue(operator.toggleIslandLock(actor, island).join());

        Predicate<Player> evicts = capturedPredicate();
        assertFalse(evicts.test(player(owner)));
        assertFalse(evicts.test(player(member)));
        assertFalse(evicts.test(player(coop)));
        assertTrue(evicts.test(player(visitor)));
        verify(database, never()).getIslandPlayers(island);
        verify(database, never()).getIslandCoops(island);
    }

    @Test
    void lockingFallsBackToTheDatabaseWhenTheSnapshotIsUnavailable() {
        when(snapshots.get(island)).thenReturn(null);
        when(database.getIslandPlayers(island)).thenReturn(Map.of(owner, "owner", member, "member"));
        when(database.getIslandCoops(island)).thenReturn(Set.of(coop));

        assertTrue(operator.toggleIslandLock(actor, island).join());

        Predicate<Player> evicts = capturedPredicate();
        assertFalse(evicts.test(player(owner)));
        assertFalse(evicts.test(player(member)));
        assertFalse(evicts.test(player(coop)));
        assertTrue(evicts.test(player(visitor)));
    }

    @Test
    void unlockingEvictsNobody() {
        when(database.toggleIslandLock(actor, island)).thenReturn(false);

        assertFalse(operator.toggleIslandLock(actor, island).join());

        verifyNoInteractions(worlds);
    }

    @SuppressWarnings("unchecked")
    private Predicate<Player> capturedPredicate() {
        ArgumentCaptor<Predicate<Player>> captor = ArgumentCaptor.forClass(Predicate.class);
        verify(worlds).removePlayersFromWorld(eq("island-" + island), captor.capture());
        return captor.getValue();
    }

    private static Player player(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }
}
