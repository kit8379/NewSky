package org.me.newsky.test;

import org.me.newsky.util.KeyedSequentialExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exercises the interleavings the island lifecycle and online-player registry rely on:
 * no two operations for one key may ever overlap, submission order is preserved, a failing
 * operation must not block the queue behind it, different keys must run in parallel, and
 * drained chains must not leak memory.
 */
public final class KeyedSequentialExecutorTest {

    public static void main(String[] args) throws Exception {
        noOverlapWithinOneKey();
        fifoOrderWithinOneKey();
        failureDoesNotBlockQueue();
        throwingSupplierDoesNotBlockQueue();
        differentKeysRunInParallel();
        chainsCleanUp();
        System.out.println("KeyedSequentialExecutorTest: ALL PASS");
    }

    // 32 threads hammer one key with 2000 operations; no two bodies may ever be active at once.
    private static void noOverlapWithinOneKey() throws Exception {
        KeyedSequentialExecutor<String> executor = new KeyedSequentialExecutor<>();
        ExecutorService pool = Executors.newFixedThreadPool(32);
        AtomicBoolean active = new AtomicBoolean(false);
        AtomicInteger overlaps = new AtomicInteger();
        AtomicInteger ran = new AtomicInteger();
        int tasks = 2000;

        CompletableFuture<?>[] all = new CompletableFuture[tasks];
        for (int i = 0; i < tasks; i++) {
            all[i] = CompletableFuture.supplyAsync(() -> executor.submit("island", () -> CompletableFuture.runAsync(() -> {
                if (!active.compareAndSet(false, true)) {
                    overlaps.incrementAndGet();
                }
                ran.incrementAndGet();
                long spinUntil = System.nanoTime() + 20_000L;
                while (System.nanoTime() < spinUntil) {
                    Thread.onSpinWait();
                }
                active.set(false);
            }, pool)), pool).thenCompose(f -> f);
        }

        CompletableFuture.allOf(all).get(60, TimeUnit.SECONDS);
        pool.shutdown();
        Check.that(ran.get() == tasks, "all " + tasks + " operations ran (ran=" + ran.get() + ")");
        Check.that(overlaps.get() == 0, "zero overlapping executions within one key (overlaps=" + overlaps.get() + ")");
    }

    // Submitted from one thread, operations must run in exactly submission order.
    private static void fifoOrderWithinOneKey() throws Exception {
        KeyedSequentialExecutor<String> executor = new KeyedSequentialExecutor<>();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Integer> order = new ArrayList<>();
        int tasks = 500;

        CompletableFuture<?>[] all = new CompletableFuture[tasks];
        for (int i = 0; i < tasks; i++) {
            int sequence = i;
            all[i] = executor.submit("island", () -> CompletableFuture.runAsync(() -> {
                synchronized (order) {
                    order.add(sequence);
                }
            }, pool));
        }

        CompletableFuture.allOf(all).get(30, TimeUnit.SECONDS);
        pool.shutdown();

        boolean sorted = true;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i) != i) {
                sorted = false;
                break;
            }
        }
        Check.that(order.size() == tasks, "all operations ran exactly once");
        Check.that(sorted, "operations ran in submission order");
    }

    // A failed operation must fail its own future and leave the queue behind it untouched.
    private static void failureDoesNotBlockQueue() throws Exception {
        KeyedSequentialExecutor<String> executor = new KeyedSequentialExecutor<>();

        CompletableFuture<Void> failing = executor.submit("island", () -> CompletableFuture.failedFuture(new RuntimeException("boom")));
        CompletableFuture<String> next = executor.submit("island", () -> CompletableFuture.completedFuture("survived"));

        Check.that(failing.handle((r, e) -> e != null).get(5, TimeUnit.SECONDS), "failing operation completes exceptionally");
        Check.that("survived".equals(next.get(5, TimeUnit.SECONDS)), "operation queued behind a failure still runs");
    }

    // A supplier that throws (instead of returning a failed future) must behave the same way.
    private static void throwingSupplierDoesNotBlockQueue() throws Exception {
        KeyedSequentialExecutor<String> executor = new KeyedSequentialExecutor<>();

        CompletableFuture<Void> throwing = executor.submit("island", () -> {
            throw new IllegalStateException("supplier blew up");
        });
        CompletableFuture<String> next = executor.submit("island", () -> CompletableFuture.completedFuture("survived"));

        Check.that(throwing.handle((r, e) -> e != null).get(5, TimeUnit.SECONDS), "throwing supplier completes its future exceptionally");
        Check.that("survived".equals(next.get(5, TimeUnit.SECONDS)), "operation queued behind a throwing supplier still runs");
    }

    // Two different keys must be able to be inside their bodies at the same time; if the executor
    // wrongly serialized across keys, the barrier would never be crossed and this would time out.
    private static void differentKeysRunInParallel() throws Exception {
        KeyedSequentialExecutor<String> executor = new KeyedSequentialExecutor<>();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CyclicBarrier bothInside = new CyclicBarrier(2);

        Runnable body = () -> {
            try {
                bothInside.await(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        CompletableFuture<Void> a = executor.submit("island-a", () -> CompletableFuture.runAsync(body, pool));
        CompletableFuture<Void> b = executor.submit("island-b", () -> CompletableFuture.runAsync(body, pool));

        CompletableFuture.allOf(a, b).get(10, TimeUnit.SECONDS);
        pool.shutdown();
        Check.that(true, "operations on different keys overlapped (no cross-key serialization)");
    }

    // Drained chains must remove themselves so idle keys hold no memory.
    private static void chainsCleanUp() throws Exception {
        KeyedSequentialExecutor<String> executor = new KeyedSequentialExecutor<>();
        ExecutorService pool = Executors.newFixedThreadPool(4);

        CompletableFuture<?>[] all = new CompletableFuture[100];
        for (int i = 0; i < all.length; i++) {
            String key = "island-" + (i % 10);
            all[i] = executor.submit(key, () -> CompletableFuture.runAsync(() -> {
            }, pool));
        }
        CompletableFuture.allOf(all).get(30, TimeUnit.SECONDS);
        pool.shutdown();

        long deadline = System.currentTimeMillis() + 2000L;
        while (executor.pendingKeys() != 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        Check.that(executor.pendingKeys() == 0, "all chains cleaned up after draining (pending=" + executor.pendingKeys() + ")");
    }
}
