package org.me.newsky.test;

import org.me.newsky.cluster.ServerRegistry;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XReadParams;
import redis.clients.jedis.resps.StreamEntry;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real-Redis adversarial verification of the messenger's boot-scoped at-most-once cursor model. */
public final class RedisStreamDeliveryExtremeTest {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;

        try (Jedis probe = new Jedis(host, port)) {
            probe.ping();
        } catch (Exception error) {
            System.out.println("RedisStreamDeliveryExtremeTest: SKIPPED (Redis unavailable: " + error.getMessage() + ")");
            return;
        }

        String prefix = "newsky:test:stream:" + UUID.randomUUID() + ":";
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(32);
        config.setMaxIdle(16);
        try (JedisPool pool = new JedisPool(config, host, port)) {
            try {
                responseSuccessPlusDeleteFailureNeverReplaysWithinBoot(pool, prefix);
                bootRegistrationClearsOnlyThePreviousInbox(pool, prefix);
                concurrentProducersLoseAndDuplicateNothing(pool, prefix);
                System.out.println("RedisStreamDeliveryExtremeTest: ALL PASS");
            } finally {
                try (Jedis jedis = pool.getResource()) {
                    jedis.keys(prefix + "*").forEach(jedis::del);
                }
            }
        }
    }

    private static void responseSuccessPlusDeleteFailureNeverReplaysWithinBoot(JedisPool pool, String prefix) {
        String inbox = prefix + "inbox";
        String responses = prefix + "responses";
        int requests = 500;

        try (Jedis jedis = pool.getResource()) {
            for (int i = 0; i < requests; i++) {
                jedis.xadd(inbox, XAddParams.xAddParams(), Map.of("message", "request-" + i));
            }

            StreamEntryID cursor = new StreamEntryID(0L, 0L);
            Set<String> handled = new HashSet<>();
            int retainedAfterDeleteFailure = 0;
            while (handled.size() < requests) {
                List<Map.Entry<String, List<StreamEntry>>> read = jedis.xread(
                        XReadParams.xReadParams().count(13), Collections.singletonMap(inbox, cursor));
                Check.silently(read != null && !read.isEmpty(), "cursor can read every queued request");
                for (StreamEntry entry : read.getFirst().getValue()) {
                    cursor = entry.getID(); // production advances before handler dispatch
                    Check.silently(handled.add(cursor.toString()), "one boot never handles an ID twice");
                    jedis.xadd(responses, XAddParams.xAddParams(),
                            Map.of("correlation", cursor.toString())); // response send succeeds
                    if (handled.size() % 3 == 0) {
                        retainedAfterDeleteFailure++; // simulate XDEL failure
                    } else {
                        jedis.xdel(inbox, cursor);
                    }
                }
            }

            List<Map.Entry<String, List<StreamEntry>>> replay = jedis.xread(
                    XReadParams.xReadParams().count(100), Collections.singletonMap(inbox, cursor));
            Check.that(replay == null || replay.isEmpty(),
                    "undeleted entries older than the boot cursor are never replayed");
            Check.that(jedis.xlen(responses) == requests,
                    "every handled request emitted exactly one response before simulated XDEL failures");
            Check.that(jedis.xlen(inbox) == retainedAfterDeleteFailure,
                    "simulated delete failures remain physically present, proving the cursor, not XDEL, prevents replay");
        }
    }

    private static void bootRegistrationClearsOnlyThePreviousInbox(JedisPool pool, String prefix) {
        String heartbeat = prefix + "heartbeat:server-a";
        String gameHeartbeat = prefix + "game-heartbeat:server-a";
        String inbox = prefix + "boot-inbox";
        String first = UUID.randomUUID().toString();
        String competitor = UUID.randomUUID().toString();

        try (Jedis jedis = pool.getResource()) {
            jedis.xadd(inbox, XAddParams.xAddParams(), Map.of("message", "dead-boot-work"));
            Object registration = jedis.eval(ServerRegistry.RENEW_INSTANCE,
                    List.of(heartbeat, gameHeartbeat, inbox), List.of(first, "30", "0"));
            Check.that(Long.valueOf(1L).equals(registration) && jedis.xlen(inbox) == 0L,
                    "new boot heartbeat publication atomically retires the dead boot inbox");

            jedis.xadd(inbox, XAddParams.xAddParams(), Map.of("message", "live-boot-work"));
            Object renewal = jedis.eval(ServerRegistry.RENEW_INSTANCE,
                    List.of(heartbeat, gameHeartbeat, inbox), List.of(first, "30", "0"));
            Object refused = jedis.eval(ServerRegistry.RENEW_INSTANCE,
                    List.of(heartbeat, gameHeartbeat, inbox), List.of(competitor, "30", "0"));
            Check.that(Long.valueOf(1L).equals(renewal) && Long.valueOf(0L).equals(refused)
                            && jedis.xlen(inbox) == 1L,
                    "same-boot renewal and refused competitor both preserve current work");
        }
    }

    private static void concurrentProducersLoseAndDuplicateNothing(JedisPool pool, String prefix) throws Exception {
        String stream = prefix + "concurrent";
        int producers = 8;
        int perProducer = 1_000;
        int expected = producers * perProducer;
        ExecutorService executor = Executors.newFixedThreadPool(producers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(producers);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        try {
            for (int producer = 0; producer < producers; producer++) {
                int producerId = producer;
                executor.execute(() -> {
                    try (Jedis jedis = pool.getResource()) {
                        start.await();
                        for (int sequence = 0; sequence < perProducer; sequence++) {
                            jedis.xadd(stream, XAddParams.xAddParams(),
                                    Map.of("message", producerId + ":" + sequence));
                        }
                    } catch (Throwable error) {
                        failure.compareAndSet(null, error);
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            StreamEntryID cursor = new StreamEntryID(0L, 0L);
            Set<String> ids = new HashSet<>(expected);
            Set<String> payloads = new HashSet<>(expected);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            try (Jedis consumer = pool.getResource()) {
                while ((done.getCount() > 0 || ids.size() < expected) && System.nanoTime() < deadline) {
                    List<Map.Entry<String, List<StreamEntry>>> read = consumer.xread(
                            XReadParams.xReadParams().block(100).count(97),
                            Collections.singletonMap(stream, cursor));
                    if (read == null) {
                        continue;
                    }
                    for (StreamEntry entry : read.getFirst().getValue()) {
                        cursor = entry.getID();
                        Check.silently(ids.add(cursor.toString()), "Redis stream IDs are unique under producer races");
                        Check.silently(payloads.add(entry.getFields().get("message")),
                                "every producer sequence is consumed once");
                    }
                }
            }

            Check.that(done.await(5, TimeUnit.SECONDS), "all eight concurrent stream producers finish");
            Check.that(failure.get() == null, "concurrent producers report no client/Redis failure");
            Check.that(ids.size() == expected && payloads.size() == expected,
                    "8,000 concurrent messages are consumed with zero loss and zero duplication");
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
