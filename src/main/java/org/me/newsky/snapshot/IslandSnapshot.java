package org.me.newsky.snapshot;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.model.Island;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * In-memory state for islands hosted on this server. It is seeded once at load, then updated with
 * the exact delta committed by each write. Work for the same island is always applied in order.
 */
public class IslandSnapshot {

    @FunctionalInterface
    public interface Reader {
        Island read(UUID islandUuid);
    }

    @FunctionalInterface
    public interface ErrorSink {
        void error(String message, Throwable error);
    }

    private final Executor executor;
    private final Reader reader;
    private final ErrorSink errorSink;

    private final Map<UUID, Island> islands = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> chains = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> pendingSeeds = new ConcurrentHashMap<>();

    // Stops a database read started before unload from restoring an old snapshot afterwards.
    private final Map<UUID, Long> unloadGenerations = new ConcurrentHashMap<>();

    public IslandSnapshot(NewSky plugin, DatabaseHandler database) {
        this(plugin.getBukkitAsyncExecutor(), database::getIslandSnapshot, plugin::severe);
    }

    public IslandSnapshot(Executor executor, Reader reader, ErrorSink errorSink) {
        this.executor = executor;
        this.reader = reader;
        this.errorSink = errorSink;
    }

    public Island get(UUID islandUuid) {
        return islands.get(islandUuid);
    }

    public CompletableFuture<Void> load(UUID islandUuid) {
        return pendingSeeds.computeIfAbsent(islandUuid, this::queueSeed);
    }

    private CompletableFuture<Void> queueSeed(UUID islandUuid) {
        long generation = currentGeneration(islandUuid);

        CompletableFuture<Void> seed = chains.compute(islandUuid, (uuid, previous) -> {
            return settled(previous).thenCompose(v -> seedFromDatabase(uuid, generation));
        });

        seed.whenComplete((result, error) -> pendingSeeds.remove(islandUuid, seed));
        return seed;
    }

    public CompletableFuture<Void> apply(UUID islandUuid, UnaryOperator<Island> delta) {
        CompletableFuture<Void> done = new CompletableFuture<>();

        CompletableFuture<Void> chained = chains.computeIfPresent(islandUuid, (uuid, previous) -> {
            return settled(previous).thenRun(() -> applyDelta(uuid, delta, done));
        });

        if (chained == null) {
            done.complete(null);
        }

        return done;
    }

    private void applyDelta(UUID islandUuid, UnaryOperator<Island> delta,
                            CompletableFuture<Void> result) {
        try {
            islands.computeIfPresent(islandUuid, (uuid, current) -> delta.apply(current));
            result.complete(null);
        } catch (Throwable error) {
            errorSink.error("Failed to apply snapshot delta for island: " + islandUuid, error);
            result.completeExceptionally(error);
        }
    }

    public CompletableFuture<Void> applyVersioned(UUID islandUuid, long committedVersion,
                                                  UnaryOperator<Island> delta) {
        CompletableFuture<Void> chained = chains.computeIfPresent(islandUuid, (uuid, previous) -> {
            return settled(previous).thenCompose(v -> {
                return applyVersionedDelta(uuid, committedVersion, delta);
            });
        });

        return chained == null ? CompletableFuture.completedFuture(null) : chained;
    }

    private CompletableFuture<Void> applyVersionedDelta(UUID islandUuid, long committedVersion,
                                                         UnaryOperator<Island> delta) {
        Island current = islands.get(islandUuid);
        if (current == null || committedVersion <= current.getStateVersion()) {
            return CompletableFuture.completedFuture(null);
        }

        if (committedVersion == current.getStateVersion() + 1) {
            return applyNextVersion(islandUuid, committedVersion, delta);
        }

        errorSink.error("Snapshot version gap for island " + islandUuid + ": memory="
                        + current.getStateVersion() + ", committed=" + committedVersion + "; reconciling",
                new IllegalStateException("Snapshot delta sequence contains a gap"));
        return seedFromDatabase(islandUuid, currentGeneration(islandUuid));
    }

    private CompletableFuture<Void> applyNextVersion(UUID islandUuid, long committedVersion,
                                                     UnaryOperator<Island> delta) {
        try {
            islands.computeIfPresent(islandUuid, (uuid, current) -> {
                return delta.apply(current).withStateVersion(committedVersion);
            });
            return CompletableFuture.completedFuture(null);
        } catch (Throwable error) {
            errorSink.error("Failed to apply versioned snapshot delta for island: "
                    + islandUuid, error);
            return CompletableFuture.failedFuture(error);
        }
    }

    public void unload(UUID islandUuid) {
        unloadGenerations.merge(islandUuid, 1L, Long::sum);
        islands.remove(islandUuid);
        chains.remove(islandUuid);
        pendingSeeds.remove(islandUuid);
    }

    private long currentGeneration(UUID islandUuid) {
        return unloadGenerations.getOrDefault(islandUuid, 0L);
    }

    private CompletableFuture<Void> seedFromDatabase(UUID islandUuid, long generation) {
        return CompletableFuture.supplyAsync(() -> reader.read(islandUuid), executor).thenAccept(island -> {
            if (island == null) {
                islands.remove(islandUuid);
                throw new IllegalStateException("Island snapshot does not exist: " + islandUuid);
            }

            if (currentGeneration(islandUuid) != generation) {
                return;
            }

            islands.put(islandUuid, island);

            if (currentGeneration(islandUuid) != generation) {
                islands.remove(islandUuid, island);
            }
        }).exceptionallyCompose(error -> {
            errorSink.error("Failed to seed island snapshot: " + islandUuid, error);
            return CompletableFuture.failedFuture(error);
        });
    }

    private CompletableFuture<Void> settled(CompletableFuture<Void> previous) {
        if (previous == null) {
            return CompletableFuture.completedFuture(null);
        }
        return previous.handle((result, error) -> null);
    }
}
