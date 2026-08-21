package org.me.newsky.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.cluster.ServerRegistry;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.WrongIslandHostException;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Island;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.XAddParams;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cross-store adversarial tests for the exact Redis Lua and MySQL row fencing used in production.
 * Every Redis key has a random namespace and MySQL uses a scratch database dropped in finally.
 */
public final class ClusterFencingExtremeTest {

    private static final String TABLE_PREFIX = "newsky_";

    public static void main(String[] args) throws Exception {
        String redisHost = args.length > 0 ? args[0] : "127.0.0.1";
        int redisPort = args.length > 1 ? Integer.parseInt(args[1]) : 6379;
        String mysqlHost = args.length > 2 ? args[2] : "127.0.0.1";
        int mysqlPort = args.length > 3 ? Integer.parseInt(args[3]) : 3306;
        String mysqlUser = args.length > 4 ? args[4] : "root";
        String mysqlPassword = args.length > 5 ? args[5] : "";

        try (Jedis probe = new Jedis(redisHost, redisPort)) {
            probe.ping();
        } catch (Exception error) {
            System.out.println("ClusterFencingExtremeTest: SKIPPED (Redis unavailable: " + error.getMessage() + ")");
            return;
        }

        String serverUrl = "jdbc:mysql://" + mysqlHost + ":" + mysqlPort
                + "/?useSSL=false&allowPublicKeyRetrieval=true";
        String scratch = "newsky_extreme_" + Long.toHexString(System.nanoTime());
        try (Connection probe = DriverManager.getConnection(serverUrl, mysqlUser, mysqlPassword);
             Statement statement = probe.createStatement()) {
            statement.executeUpdate("CREATE DATABASE " + scratch);
        } catch (Exception error) {
            System.out.println("ClusterFencingExtremeTest: SKIPPED (MySQL unavailable: " + error.getMessage() + ")");
            return;
        }

        String redisPrefix = "newsky:test:extreme:" + UUID.randomUUID() + ":";
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(96);
        poolConfig.setMaxIdle(32);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + scratch
                + "?useSSL=false&allowPublicKeyRetrieval=true");
        hikariConfig.setUsername(mysqlUser);
        hikariConfig.setPassword(mysqlPassword);
        hikariConfig.setMaximumPoolSize(24);

        try (JedisPool redis = new JedisPool(poolConfig, redisHost, redisPort);
             HikariDataSource dataSource = new HikariDataSource(hikariConfig)) {
            DatabaseHandler database = new DatabaseHandler(dataSource, TABLE_PREFIX, "0,64,0,0,0",
                    (message, error) -> {
                    });

            sameServerNameRegistrationStormHasOneIncarnation(redis, redisPrefix);
            redisTakeoverAndMysqlEpochFenceAgree(redis, database, redisPrefix);
            rowLockHandoffCannotOvertakeAnAlreadyValidatedCommit(database, dataSource);
            System.out.println("ClusterFencingExtremeTest: ALL PASS");
        } finally {
            try (Jedis cleanup = new Jedis(redisHost, redisPort)) {
                cleanup.keys(redisPrefix + "*").forEach(cleanup::del);
            }
            try (Connection cleanup = DriverManager.getConnection(serverUrl, mysqlUser, mysqlPassword);
                 Statement statement = cleanup.createStatement()) {
                statement.executeUpdate("DROP DATABASE IF EXISTS " + scratch);
            }
        }
    }

    private static void sameServerNameRegistrationStormHasOneIncarnation(JedisPool pool, String prefix) throws Exception {
        String serverName = "same-name";
        String heartbeat = prefix + "heartbeat:" + serverName;
        String gameHeartbeat = prefix + "game-heartbeat:" + serverName;
        String inbox = prefix + "inbox:" + serverName;
        String mspt = prefix + "mspt";
        int contenders = 64;

        List<String> instances = new ArrayList<>(contenders);
        for (int i = 0; i < contenders; i++) {
            instances.add(UUID.randomUUID().toString());
        }

        try (Jedis jedis = pool.getResource()) {
            jedis.xadd(inbox, XAddParams.xAddParams(), Map.of("message", "previous-boot"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(contenders);
        AtomicInteger winners = new AtomicInteger();
        AtomicReference<String> winner = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        try {
            for (String instance : instances) {
                executor.execute(() -> {
                    try (Jedis jedis = pool.getResource()) {
                        start.await();
                        Object result = jedis.eval(ServerRegistry.RENEW_INSTANCE,
                                List.of(heartbeat, gameHeartbeat, inbox),
                                List.of(instance, "30", "0"));
                        if (Long.valueOf(1L).equals(result)) {
                            winners.incrementAndGet();
                            winner.set(instance);
                        }
                    } catch (Throwable error) {
                        failure.compareAndSet(null, error);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            Check.that(done.await(30, TimeUnit.SECONDS), "64 same-name startup contenders finish");
            Check.that(failure.get() == null, "same-name startup storm has no Redis/client failure");
            Check.that(winners.get() == 1, "exactly one JVM incarnation owns a duplicated server name");

            try (Jedis jedis = pool.getResource()) {
                String live = jedis.get(heartbeat);
                Check.that(winner.get().equals(live) && live.equals(jedis.get(gameHeartbeat)),
                        "general and game heartbeats publish the same winning incarnation");
                Check.that(jedis.xlen(inbox) == 0L,
                        "the winning first registration clears the previous boot inbox exactly once");

                jedis.hset(mspt, serverName, "42.0");
                for (String loser : instances) {
                    if (loser.equals(live)) {
                        continue;
                    }
                    Object staleShutdown = jedis.eval(ServerRegistry.REMOVE_INSTANCE_IF_CURRENT,
                            List.of(heartbeat, gameHeartbeat, mspt), List.of(loser, serverName));
                    Check.silently(Long.valueOf(0L).equals(staleShutdown),
                            "a losing startup cannot run shutdown cleanup on the winner");
                }
                Check.that(live.equals(jedis.get(heartbeat)) && "42.0".equals(jedis.hget(mspt, serverName)),
                        "all 63 stale shutdowns preserve the winner and its metrics");

                jedis.xadd(inbox, XAddParams.xAddParams(), Map.of("message", "current-boot"));
                Object renewal = jedis.eval(ServerRegistry.RENEW_INSTANCE,
                        List.of(heartbeat, gameHeartbeat, inbox), List.of(live, "30", "0"));
                Check.that(Long.valueOf(1L).equals(renewal) && jedis.xlen(inbox) == 1L,
                        "winner renewal cannot erase current-boot work");
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void redisTakeoverAndMysqlEpochFenceAgree(JedisPool pool, DatabaseHandler database,
                                                               String prefix) {
        UUID island = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID banned = UUID.randomUUID();
        UUID oldEpoch = UUID.randomUUID();
        UUID newEpoch = UUID.randomUUID();
        UUID finalEpoch = UUID.randomUUID();
        Actor actor = new Actor.Player(owner);

        IslandRegistry.HostClaim oldHost = new IslandRegistry.HostClaim("old-host", oldEpoch.toString());
        IslandRegistry.HostClaim newHost = new IslandRegistry.HostClaim("new-host", newEpoch.toString());
        IslandRegistry.HostClaim finalHost = new IslandRegistry.HostClaim("final-host", finalEpoch.toString());
        String claims = prefix + "claims";
        String oldHeartbeat = prefix + "hb:old-host";
        String newHeartbeat = prefix + "hb:new-host";
        String finalHeartbeat = prefix + "hb:final-host";

        database.createIsland(island, owner, oldEpoch);
        try (Jedis jedis = pool.getResource()) {
            jedis.set(oldHeartbeat, oldEpoch.toString());
            jedis.set(newHeartbeat, newEpoch.toString());
            jedis.set(finalHeartbeat, finalEpoch.toString());
            Object firstClaim = jedis.eval(IslandRegistry.CLAIM_IF_LIVE,
                    List.of(claims, oldHeartbeat),
                    List.of(island.toString(), oldHost.encoded(), oldHost.instanceId()));
            Check.that(Long.valueOf(1L).equals(firstClaim), "old host initially owns the Redis claim");

            jedis.del(oldHeartbeat);
            Object reaped = jedis.eval(IslandRegistry.RELEASE_IF_DEAD,
                    List.of(claims, oldHeartbeat),
                    List.of(island.toString(), oldHost.encoded(), oldHost.instanceId()));
            Object takeover = jedis.eval(IslandRegistry.CLAIM_IF_LIVE,
                    List.of(claims, newHeartbeat),
                    List.of(island.toString(), newHost.encoded(), newHost.instanceId()));
            Check.that(Long.valueOf(1L).equals(reaped) && Long.valueOf(1L).equals(takeover),
                    "expired old claim is reaped and exactly replaced by the new host");
        }

        database.bindWriteEpoch(island, newEpoch);
        boolean staleWriteFenced = false;
        try {
            database.addBan(island, actor, banned, oldEpoch);
        } catch (WrongIslandHostException expected) {
            staleWriteFenced = true;
        }
        Check.that(staleWriteFenced && !database.getIslandSnapshot(island).getBans().contains(banned),
                "old JVM resurrection cannot commit after the MySQL epoch handoff");

        boolean stalePublishFenced = false;
        try {
            database.markIslandReady(island, oldEpoch);
        } catch (WrongIslandHostException expected) {
            stalePublishFenced = true;
        }
        Check.that(stalePublishFenced && database.isIslandProvisioning(island),
                "old JVM cannot publish another host's provisioning island");

        boolean staleCleanupFenced = false;
        try {
            database.deleteIsland(island, new Actor.Bypass("stale create cleanup"), oldEpoch);
        } catch (WrongIslandHostException expected) {
            staleCleanupFenced = true;
        }
        Check.that(staleCleanupFenced && database.getIslandSnapshot(island) != null,
                "late create cleanup cannot delete rows after another host rebound the epoch");

        try (Jedis jedis = pool.getResource()) {
            jedis.set(oldHeartbeat, oldEpoch.toString()); // the paused old process comes back
            Object resurrected = jedis.eval(IslandRegistry.CLAIM_OR_CONFIRM,
                    List.of(claims, oldHeartbeat),
                    List.of(island.toString(), oldHost.encoded(), oldHost.instanceId()));
            Check.that(Long.valueOf(0L).equals(resurrected)
                            && newHost.encoded().equals(jedis.hget(claims, island.toString())),
                    "resurrected old process cannot reclaim beside the new live holder");
        }

        database.markIslandReady(island, newEpoch);
        database.addBan(island, actor, banned, newEpoch);
        UUID operationId = UUID.randomUUID();
        DatabaseHandler.VersionedBoolean firstToggle =
                database.toggleLockVersioned(island, actor, operationId, newEpoch);

        try (Jedis jedis = pool.getResource()) {
            jedis.del(newHeartbeat);
            jedis.eval(IslandRegistry.RELEASE_IF_DEAD, List.of(claims, newHeartbeat),
                    List.of(island.toString(), newHost.encoded(), newHost.instanceId()));
            Object claimed = jedis.eval(IslandRegistry.CLAIM_IF_LIVE, List.of(claims, finalHeartbeat),
                    List.of(island.toString(), finalHost.encoded(), finalHost.instanceId()));
            Check.that(Long.valueOf(1L).equals(claimed), "a third host can take over after the second lease dies");
        }

        database.bindWriteEpoch(island, finalEpoch);
        DatabaseHandler.VersionedBoolean replay =
                database.toggleLockVersioned(island, actor, operationId, finalEpoch);
        Island snapshot = database.getIslandSnapshot(island);
        Check.that(replay.value() == firstToggle.value()
                        && snapshot.isLock() == firstToggle.value()
                        && snapshot.getStateVersion() == firstToggle.version(),
                "operation-ID replay across host takeover returns the first result without toggling twice");
    }

    private static void rowLockHandoffCannotOvertakeAnAlreadyValidatedCommit(DatabaseHandler database,
                                                                               HikariDataSource dataSource) throws Exception {
        UUID island = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID oldEpoch = UUID.randomUUID();
        UUID newEpoch = UUID.randomUUID();
        database.createIsland(island, owner, oldEpoch);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch binderEntered = new CountDownLatch(1);
        try (Connection oldTransaction = dataSource.getConnection()) {
            oldTransaction.setAutoCommit(false);
            try (PreparedStatement lock = oldTransaction.prepareStatement("SELECT write_epoch FROM "
                    + TABLE_PREFIX + "islands WHERE island_uuid = ? FOR UPDATE")) {
                lock.setString(1, island.toString());
                try (ResultSet result = lock.executeQuery()) {
                    Check.that(result.next() && oldEpoch.toString().equals(result.getString(1)),
                            "old transaction validates its epoch while holding the island row lock");
                }
            }

            CompletableFuture<Void> takeoverBind = CompletableFuture.runAsync(() -> {
                binderEntered.countDown();
                database.bindWriteEpoch(island, newEpoch);
            }, executor);
            Check.that(binderEntered.await(5, TimeUnit.SECONDS), "new host starts its epoch bind");
            Thread.sleep(150L);
            Check.that(!takeoverBind.isDone(), "new epoch bind waits behind the validated old transaction");

            try (PreparedStatement update = oldTransaction.prepareStatement("UPDATE " + TABLE_PREFIX
                    + "islands SET pvp = TRUE, state_version = state_version + 1 WHERE island_uuid = ?")) {
                update.setString(1, island.toString());
                Check.that(update.executeUpdate() == 1, "already-validated old transaction writes exactly one row");
            }
            oldTransaction.commit();
            takeoverBind.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        Island seededAfterHandoff = database.getIslandSnapshot(island);
        Check.that(seededAfterHandoff.isPvp() && seededAfterHandoff.getStateVersion() == 1L,
                "new host seeds after the preceding old commit and cannot miss it");

        boolean oldRejected = false;
        try {
            database.togglePvpVersioned(island, new Actor.Player(owner), UUID.randomUUID(), oldEpoch);
        } catch (WrongIslandHostException expected) {
            oldRejected = true;
        }
        Check.that(oldRejected, "every old transaction starting after the handoff is fenced");
    }
}
