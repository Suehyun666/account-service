package infrastructure.shard;

import infrastructure.metrics.ShardMetrics;
import jakarta.annotation.PreDestroy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class ShardExecutor {

    private static final AtomicInteger SHARD_COUNTER = new AtomicInteger(0);

    private final int shardId;
    private final ExecutorService executor;
    private final ShardMetrics metrics;
    private final AtomicInteger queueDepth = new AtomicInteger(0);

    public ShardExecutor(ShardMetrics metrics) {
        this.shardId = SHARD_COUNTER.getAndIncrement();
        this.metrics = metrics;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("account-shard-" + shardId);
            t.setDaemon(true);
            return t;
        });
    }

    public void execute(Runnable task) {
        long submitNanos = System.nanoTime();
        queueDepth.incrementAndGet();
        metrics.updateQueueDepth(shardId, queueDepth.get());

        executor.submit(() -> {
            long startNanos = System.nanoTime();
            long waitNanos = startNanos - submitNanos;
            metrics.recordWaitTime(shardId, waitNanos);

            try {
                task.run();
            } finally {
                long endNanos = System.nanoTime();
                long processingNanos = endNanos - startNanos;
                metrics.recordProcessingTime(shardId, processingNanos);

                queueDepth.decrementAndGet();
                metrics.updateQueueDepth(shardId, queueDepth.get());
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
