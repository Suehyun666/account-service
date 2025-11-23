package infrastructure.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class ShardMetrics {

    private final MeterRegistry registry;
    private final int shardCount;
    private AtomicInteger[] queueDepths;

    @Inject
    public ShardMetrics(MeterRegistry registry,
                        @ConfigProperty(name = "account.shard.count", defaultValue = "16") int shardCount) {
        this.registry = registry;
        this.shardCount = shardCount;
    }

    @PostConstruct
    void init() {
        queueDepths = new AtomicInteger[shardCount];
        for (int i = 0; i < shardCount; i++) {
            AtomicInteger depth = new AtomicInteger(0);
            queueDepths[i] = depth;

            Gauge.builder("account_shard_queue_depth", depth, AtomicInteger::get)
                    .description("Current queue depth per shard")
                    .tag("shard", String.valueOf(i))
                    .register(registry);
        }
    }

    public void updateQueueDepth(int shardId, int depth) {
        if (shardId < 0 || shardId >= shardCount) {
            return;
        }
        queueDepths[shardId].set(depth);
    }

    public void recordProcessingTime(int shardId, long durationNanos) {
        Timer timer = Timer.builder("account_shard_processing_seconds")
                .description("Task processing latency per shard")
                .tag("shard", String.valueOf(shardId))
                .publishPercentileHistogram()
                .register(registry);

        timer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordWaitTime(int shardId, long waitNanos) {
        Timer timer = Timer.builder("account_shard_wait_seconds")
                .description("Queue waiting time per shard")
                .tag("shard", String.valueOf(shardId))
                .publishPercentileHistogram()
                .register(registry);

        timer.record(waitNanos, TimeUnit.NANOSECONDS);
    }
}
