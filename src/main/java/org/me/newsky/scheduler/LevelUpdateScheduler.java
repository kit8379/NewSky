package org.me.newsky.scheduler;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.island.LevelHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class LevelUpdateScheduler {

    private static final long BASE_INTERVAL_MS = 5 * 60_000L;
    private static final long JITTER_MS = 60_000L;
    private static final int MAX_CONCURRENT = 1;
    private static final long POLL_PERIOD_TICKS = 20L;
    private final NewSky plugin;
    private final LevelHandler levelHandler;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final PriorityQueue<Entry> pq = new PriorityQueue<>(Comparator.comparingLong(e -> e.nextRunAtMs));
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final SplittableRandom rnd = new SplittableRandom();
    private BukkitTask task;

    public LevelUpdateScheduler(NewSky plugin, LevelHandler levelHandler) {
        this.plugin = plugin;
        this.levelHandler = levelHandler;
    }

    public void start() {
        if (task != null) {
            return;
        }

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, POLL_PERIOD_TICKS, POLL_PERIOD_TICKS);
        plugin.debug("LevelUpdateScheduler", "Started (BASE_INTERVAL_MS=" + BASE_INTERVAL_MS + ", JITTER_MS=" + JITTER_MS + ", MAX_CONCURRENT=" + MAX_CONCURRENT + ", POLL_PERIOD_TICKS=" + POLL_PERIOD_TICKS + ")");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        entries.clear();
        pq.clear();

        plugin.debug("LevelUpdateScheduler", "Stopped and cleared all scheduled islands.");
    }

    public void registerIsland(UUID islandUuid) {
        unregisterIsland(islandUuid);

        long now = System.currentTimeMillis();

        long firstDelay = 1_000L + nextJitterMs();

        Entry e = new Entry(islandUuid, now + firstDelay);
        entries.put(islandUuid, e);
        pq.add(e);

        plugin.debug("LevelUpdateScheduler", "Registered island " + islandUuid + ", first run in ~" + firstDelay + "ms");
    }

    public void unregisterIsland(UUID islandUuid) {
        Entry existing = entries.remove(islandUuid);
        if (existing != null) {
            existing.cancelled = true;
            plugin.debug("LevelUpdateScheduler", "Unregistered island " + islandUuid);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();

        while (inFlight.get() < MAX_CONCURRENT) {
            Entry due = pollNextDue(now);
            if (due == null) break;

            startOne(due, now);
        }
    }

    private Entry pollNextDue(long now) {
        while (!pq.isEmpty()) {
            Entry e = pq.peek();
            if (e.cancelled) {
                pq.poll();
                continue;
            }

            if (e.nextRunAtMs > now) {
                return null;
            }

            pq.poll();

            Entry current = entries.get(e.islandUuid);
            if (current == null || current != e) continue;

            return e;
        }
        return null;
    }

    private void startOne(Entry e, long now) {
        int cur = inFlight.incrementAndGet();
        plugin.debug("LevelUpdateScheduler", "Starting calculate for " + e.islandUuid + " (inFlight=" + cur + ")");

        CompletableFuture<Integer> fut;
        try {
            // Must be main thread (LevelHandler enforces this).
            fut = levelHandler.calIslandLevel(e.islandUuid);
        } catch (Throwable t) {
            plugin.severe("LevelUpdateScheduler: Failed to start calculate for " + e.islandUuid, t);
            inFlight.decrementAndGet();
            rescheduleFailure(e.islandUuid, now);
            return;
        }

        fut.whenComplete((level, err) -> {
            // Callback may execute on any thread; reschedule bookkeeping on main thread.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                int after = inFlight.decrementAndGet();
                long now2 = System.currentTimeMillis();

                if (err != null) {
                    plugin.severe("LevelUpdateScheduler: Calculate failed for " + e.islandUuid, err);
                    rescheduleFailure(e.islandUuid, now2);
                } else {
                    rescheduleSuccess(e.islandUuid, now2);
                    plugin.debug("LevelUpdateScheduler", "Calculate done for " + e.islandUuid + " level=" + level + " (inFlight=" + after + ")");
                }
            });
        });
    }

    private void rescheduleSuccess(UUID islandUuid, long now) {
        Entry cur = entries.get(islandUuid);
        if (cur == null) return;

        long next = now + BASE_INTERVAL_MS + nextJitterMs();

        cur.cancelled = true;
        Entry ne = new Entry(islandUuid, next);
        entries.put(islandUuid, ne);
        pq.add(ne);
    }

    private void rescheduleFailure(UUID islandUuid, long now) {
        Entry cur = entries.get(islandUuid);
        if (cur == null) return;

        long retryBase = Math.max(5_000L, BASE_INTERVAL_MS / 10);
        long next = now + retryBase + nextJitterMs();

        cur.cancelled = true;
        Entry ne = new Entry(islandUuid, next);
        entries.put(islandUuid, ne);
        pq.add(ne);
    }

    private long nextJitterMs() {
        if (JITTER_MS <= 0) {
            return 0L;
        }
        return (long) (rnd.nextDouble() * (double) JITTER_MS);
    }

    private static final class Entry {
        private final UUID islandUuid;
        private final long nextRunAtMs;
        private boolean cancelled;

        private Entry(UUID islandUuid, long nextRunAtMs) {
            this.islandUuid = islandUuid;
            this.nextRunAtMs = nextRunAtMs;
        }
    }
}
