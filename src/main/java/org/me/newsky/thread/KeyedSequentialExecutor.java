package org.me.newsky.thread;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Runs asynchronous operations one at a time per key, in submission order. Operations for
 * different keys run freely in parallel; a failed operation never blocks the ones queued behind
 * it. Chains clean themselves up once drained, so idle keys hold no memory.
 * <p>
 * This is the concurrency primitive behind per-island lifecycle ordering and per-player registry
 * ordering. It is deliberately free of any plugin dependency so its guarantees are covered by
 * {@code KeyedSequentialExecutorTest}, which exercises exactly the interleavings the callers rely
 * on (no overlap within a key, isolation of failures, parallelism across keys, map cleanup).
 */
public final class KeyedSequentialExecutor<K> {

    private final Map<K, CompletableFuture<Void>> chains = new ConcurrentHashMap<>();

    /**
     * Queues the operation behind whatever is already running for this key. The returned future
     * completes with the operation's own result or failure.
     */
    public <T> CompletableFuture<T> submit(K key, Supplier<CompletableFuture<T>> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();

        CompletableFuture<Void> chain = chains.compute(key, (k, previous) -> {
            CompletableFuture<Void> settled = previous == null ? CompletableFuture.completedFuture(null) : previous.handle((r, e) -> null);

            return settled.thenCompose(v -> {
                CompletableFuture<T> op;
                try {
                    op = operation.get();
                } catch (Throwable t) {
                    op = CompletableFuture.failedFuture(t);
                }

                return op.handle((opResult, opError) -> {
                    if (opError != null) {
                        result.completeExceptionally(opError);
                    } else {
                        result.complete(opResult);
                    }
                    return null;
                });
            });
        });

        chain.whenComplete((r, e) -> chains.remove(key, chain));
        return result;
    }

    /**
     * Number of keys with work still queued or running. Exposed for tests and diagnostics.
     */
    public int pendingKeys() {
        return chains.size();
    }
}
