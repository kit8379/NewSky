package org.me.newsky.test;

import org.me.newsky.cluster.IslandRegistry;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Model-based chaos test of the island placement protocol against a real Redis, using the exact
 * production Lua scripts. Simulated servers race to claim, load, unload, crash, restart, reap dead
 * claims and replay stale load requests, mirroring the real code's ordering rules:
 * <ul>
 *   <li>a load claims-or-confirms before marking the world loaded (point-of-effect guard),</li>
 *   <li>an unload clears the world before releasing, and only via compare-and-delete,</li>
 *   <li>lifecycle operations for one island serialize per server (the KeyedSequentialExecutor
 *       role, modeled by a per-island monitor),</li>
 *   <li>a restart sweeps only its own claims; the reaper only touches heartbeat-dead holders.</li>
 * </ul>
 * The invariant under test is the one the whole design exists for: <b>no two servers ever have
 * the same island loaded at the same time</b>. Detection is deterministic, not sampled: each
 * loader sets its own flag before checking every other server's, so of two overlapping loaders at
 * least one must see the other. Needs a Redis (args: host port [ops]); prints SKIPPED without one.
 */
public final class RedisPlacementChaosTest {

    private static final int SERVERS = 4;
    private static final int ISLANDS = 6;
    private static final int DRIVER_THREADS = 16;

    private static String claimKey;
    private static String heartbeatPrefix;
    private static JedisPool pool;
    private static SimServer[] servers;
    private static String[] islandIds;
    private static Map<String, SimServer> serversByName;

    private static final List<String> violations = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger loads = new AtomicInteger();
    private static final AtomicInteger refusals = new AtomicInteger();
    private static final AtomicInteger unloads = new AtomicInteger();
    private static final AtomicInteger crashes = new AtomicInteger();
    private static final AtomicInteger restarts = new AtomicInteger();
    private static final AtomicInteger reaps = new AtomicInteger();

    private static final class SimServer {
        final String name;
        final AtomicBoolean[] loaded = new AtomicBoolean[ISLANDS];
        final Object[] lifecycle = new Object[ISLANDS];
        final ReentrantReadWriteLock crashLock = new ReentrantReadWriteLock();
        volatile boolean alive = true;
        volatile String instanceId = UUID.randomUUID().toString();

        SimServer(String name) {
            this.name = name;
            for (int i = 0; i < ISLANDS; i++) {
                loaded[i] = new AtomicBoolean(false);
                lifecycle[i] = new Object();
            }
        }

        String encoded() {
            return instanceId + ":" + name;
        }
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;
        int totalOps = args.length > 2 ? Integer.parseInt(args[2]) : 10_000;

        try (Jedis probe = new Jedis(host, port)) {
            probe.ping();
        } catch (Exception e) {
            System.out.println("RedisPlacementChaosTest: SKIPPED (no Redis reachable at " + host + ":" + port + ")");
            return;
        }

        String prefix = "newsky:test:" + UUID.randomUUID() + ":";
        claimKey = prefix + "island:server";
        heartbeatPrefix = prefix + "hb:";

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(DRIVER_THREADS * 2);

        try (JedisPool jedisPool = new JedisPool(poolConfig, host, port)) {
            pool = jedisPool;
            setUpCluster();
            try {
                runChaos(totalOps);
                assertCoverage();
                assertInvariants();
                System.out.println("RedisPlacementChaosTest: ALL PASS (ops=" + totalOps + ", loads=" + loads + ", refusals=" + refusals + ", unloads=" + unloads + ", crashes=" + crashes + ", restarts=" + restarts + ", reaps=" + reaps + ")");
            } finally {
                try (Jedis jedis = pool.getResource()) {
                    jedis.keys(prefix + "*").forEach(jedis::del);
                }
            }
        }
    }

    private static void setUpCluster() {
        servers = new SimServer[SERVERS];
        serversByName = new HashMap<>();
        try (Jedis jedis = pool.getResource()) {
            for (int i = 0; i < SERVERS; i++) {
                servers[i] = new SimServer("server-" + i);
                serversByName.put(servers[i].name, servers[i]);
                jedis.set(heartbeatPrefix + servers[i].name, servers[i].instanceId);
            }
        }

        islandIds = new String[ISLANDS];
        for (int i = 0; i < ISLANDS; i++) {
            islandIds[i] = UUID.randomUUID().toString();
        }
    }

    private static void runChaos(int totalOps) throws Exception {
        ExecutorService drivers = Executors.newFixedThreadPool(DRIVER_THREADS);
        AtomicInteger opCounter = new AtomicInteger();

        for (int t = 0; t < DRIVER_THREADS; t++) {
            drivers.execute(() -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                while (opCounter.getAndIncrement() < totalOps) {
                    try {
                        int island = rnd.nextInt(ISLANDS);
                        int dice = rnd.nextInt(100);

                        if (dice < 55) {
                            teleportDriver(island, rnd);
                        } else if (dice < 80) {
                            unloadDriver(island);
                        } else if (dice < 88) {
                            // Stale replay: a load request landing on a random server no matter
                            // who holds the claim. The point-of-effect guard must refuse it.
                            deliverLoad(servers[rnd.nextInt(SERVERS)], island);
                        } else if (dice < 93) {
                            reaper();
                        } else if (dice < 97) {
                            crash(servers[rnd.nextInt(SERVERS)]);
                        } else {
                            restart(servers[rnd.nextInt(SERVERS)]);
                        }
                    } catch (Exception e) {
                        violations.add("driver exception: " + e);
                    }
                }
            });
        }

        drivers.shutdown();
        Check.that(drivers.awaitTermination(240, TimeUnit.SECONDS), "chaos drivers finished in time");
    }

    // ensureIslandLoaded: read the claim, claim for a candidate when absent, then deliver the
    // load to whoever holds it.
    private static void teleportDriver(int island, ThreadLocalRandom rnd) {
        try (Jedis jedis = pool.getResource()) {
            String encodedHolder = jedis.hget(claimKey, islandIds[island]);
            if (encodedHolder == null) {
                SimServer candidate = servers[rnd.nextInt(SERVERS)];
                jedis.eval(IslandRegistry.CLAIM_IF_LIVE,
                        List.of(claimKey, heartbeatPrefix + candidate.name),
                        List.of(islandIds[island], candidate.encoded(), candidate.instanceId));
                encodedHolder = jedis.hget(claimKey, islandIds[island]);
            }
            if (encodedHolder != null) {
                SimServer host = serversByName.get(IslandRegistry.HostClaim.decode(encodedHolder).serverName());
                if (host != null) {
                    deliverLoad(host, island);
                }
            }
        }
    }

    // IslandOperator.doLoadIsland: claim-or-confirm at the point of effect, then load. The world
    // flag is set before scanning the other servers, so of two overlapping loaders at least one
    // is guaranteed to observe the other - detection cannot be lost to timing.
    private static void deliverLoad(SimServer host, int island) {
        host.crashLock.readLock().lock();
        try {
            if (!host.alive) {
                return; // message sat in a dead server's inbox
            }

            synchronized (host.lifecycle[island]) {
                Object verdict;
                try (Jedis jedis = pool.getResource()) {
                    verdict = jedis.eval(IslandRegistry.CLAIM_OR_CONFIRM,
                            List.of(claimKey, heartbeatPrefix + host.name),
                            List.of(islandIds[island], host.encoded(), host.instanceId));
                }

                if (!Long.valueOf(1L).equals(verdict)) {
                    refusals.incrementAndGet();
                    return;
                }

                spin();
                host.loaded[island].set(true);
                loads.incrementAndGet();

                for (SimServer other : servers) {
                    if (other != host && other.loaded[island].get()) {
                        violations.add("island " + island + " loaded on both " + host.name + " and " + other.name);
                    }
                }
            }
        } finally {
            host.crashLock.readLock().unlock();
        }
    }

    // IslandOperator.doUnloadIsland: world goes away first, then the claim is released by
    // compare-and-delete, all inside the island's lifecycle slot.
    private static void unloadDriver(int island) {
        String encodedHolder;
        try (Jedis jedis = pool.getResource()) {
            encodedHolder = jedis.hget(claimKey, islandIds[island]);
        }
        if (encodedHolder == null) {
            return;
        }

        SimServer host = serversByName.get(IslandRegistry.HostClaim.decode(encodedHolder).serverName());
        if (host == null) {
            return;
        }

        host.crashLock.readLock().lock();
        try {
            if (!host.alive) {
                return;
            }

            synchronized (host.lifecycle[island]) {
                if (!host.loaded[island].get()) {
                    return;
                }

                host.loaded[island].set(false);
                try (Jedis jedis = pool.getResource()) {
                    jedis.eval(IslandRegistry.RELEASE_IF_HELD_BY, List.of(claimKey), List.of(islandIds[island], host.encoded()));
                }
                unloads.incrementAndGet();
            }
        } finally {
            host.crashLock.readLock().unlock();
        }
    }

    // ServerRegistry.reapDeadServerClaims: any server may sweep, the Lua re-checks holder and
    // liveness atomically.
    private static void reaper() {
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> mappings = jedis.hgetAll(claimKey);
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                IslandRegistry.HostClaim claim = IslandRegistry.HostClaim.decode(entry.getValue());
                if (claim.instanceId().equals(jedis.get(heartbeatPrefix + claim.serverName()))) {
                    continue;
                }
                Object removed = jedis.eval(IslandRegistry.RELEASE_IF_DEAD,
                        List.of(claimKey, heartbeatPrefix + claim.serverName()),
                        List.of(entry.getKey(), entry.getValue(), claim.instanceId()));
                if (Long.valueOf(1L).equals(removed)) {
                    reaps.incrementAndGet();
                }
            }
        }
    }

    // An abrupt JVM death: worlds vanish, the heartbeat stops, claims are left dangling for the
    // reaper. Waits for no in-flight operation (write lock) because a real crash kills those too.
    private static void crash(SimServer server) {
        if (!server.crashLock.writeLock().tryLock()) {
            return;
        }
        try {
            if (!server.alive || aliveCount() < 2) {
                return;
            }

            for (AtomicBoolean flag : server.loaded) {
                flag.set(false);
            }
            try (Jedis jedis = pool.getResource()) {
                jedis.del(heartbeatPrefix + server.name);
            }
            server.alive = false;
            crashes.incrementAndGet();
        } finally {
            server.crashLock.writeLock().unlock();
        }
    }

    // HeartbeatScheduler startup: sweep own leftover claims (compare-and-delete each), then
    // start heartbeating again.
    private static void restart(SimServer server) {
        if (!server.crashLock.writeLock().tryLock()) {
            return;
        }
        try {
            if (server.alive) {
                return;
            }

            try (Jedis jedis = pool.getResource()) {
                String oldEncoded = server.encoded();
                Map<String, String> mappings = jedis.hgetAll(claimKey);
                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    if (oldEncoded.equals(entry.getValue())) {
                        jedis.eval(IslandRegistry.RELEASE_IF_HELD_BY, List.of(claimKey), List.of(entry.getKey(), oldEncoded));
                    }
                }
                server.instanceId = UUID.randomUUID().toString();
                jedis.set(heartbeatPrefix + server.name, server.instanceId);
            }
            server.alive = true;
            restarts.incrementAndGet();
        } finally {
            server.crashLock.writeLock().unlock();
        }
    }

    private static int aliveCount() {
        int count = 0;
        for (SimServer server : servers) {
            if (server.alive) {
                count++;
            }
        }
        return count;
    }

    private static void spin() {
        long until = System.nanoTime() + 50_000L;
        while (System.nanoTime() < until) {
            Thread.onSpinWait();
        }
    }

    // A chaos run that never crashed, reaped or refused anything proved nothing; require every
    // scenario to have actually happened.
    private static void assertCoverage() {
        Check.that(loads.get() > 0, "loads happened (" + loads + ")");
        Check.that(unloads.get() > 0, "unloads happened (" + unloads + ")");
        Check.that(refusals.get() > 0, "stale/replayed loads were refused by the point-of-effect guard (" + refusals + ")");
        Check.that(crashes.get() > 0, "servers crashed mid-run (" + crashes + ")");
        Check.that(restarts.get() > 0, "servers restarted mid-run (" + restarts + ")");
        Check.that(reaps.get() > 0, "the reaper cleaned dead claims (" + reaps + ")");
    }

    private static void assertInvariants() {
        Check.that(violations.isEmpty(), "no island was ever loaded on two servers at once (violations=" + violations + ")");

        try (Jedis jedis = pool.getResource()) {
            for (int island = 0; island < ISLANDS; island++) {
                List<SimServer> loaders = new ArrayList<>();
                for (SimServer server : servers) {
                    if (server.loaded[island].get()) {
                        loaders.add(server);
                    }
                }

                Check.that(loaders.size() <= 1, "island " + island + " has at most one loader at rest (loaders=" + loaders.size() + ")");
                if (loaders.size() == 1) {
                    String holder = jedis.hget(claimKey, islandIds[island]);
                    IslandRegistry.HostClaim claim = IslandRegistry.HostClaim.decode(holder);
                    Check.that(loaders.get(0).name.equals(claim.serverName()) && loaders.get(0).instanceId.equals(claim.instanceId()),
                            "island " + island + "'s claim matches its loader incarnation at rest (claim=" + holder + ", loader=" + loaders.get(0).encoded() + ")");
                }
            }
        }
    }
}
