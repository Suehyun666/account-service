package infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class RedisMetrics {

    private final MeterRegistry registry;

    @Inject
    public RedisMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordGet(long durationNanos) {
        Timer timer = Timer.builder("account_redis_get_seconds")
                .description("Redis GET latency")
                .publishPercentileHistogram()
                .register(registry);
        timer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordSet(long durationNanos) {
        Timer timer = Timer.builder("account_redis_set_seconds")
                .description("Redis SET latency")
                .publishPercentileHistogram()
                .register(registry);
        timer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void incrementFailure(String op) {
        Counter c = Counter.builder("account_redis_failure_total")
                .description("Redis operation failures")
                .tag("op", op)
                .register(registry);
        c.increment();
    }

    public void incrementTimeout() {
        Counter c = Counter.builder("account_redis_timeout_total")
                .description("Redis timeouts")
                .register(registry);
        c.increment();
    }

    public void incrementScriptError() {
        Counter c = Counter.builder("account_redis_script_error_total")
                .description("Redis Lua script errors")
                .register(registry);
        c.increment();
    }

    public void incrementClusterRedirect() {
        Counter c = Counter.builder("account_redis_cluster_redirect_total")
                .description("Redis MOVED/ASK redirects")
                .register(registry);
        c.increment();
    }
}
