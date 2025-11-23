package infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class DbMetrics {

    private final MeterRegistry registry;

    @Inject
    public DbMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordWrite(String op, long durationNanos) {
        Timer timer = Timer.builder("account_db_write_seconds")
                .description("DB write latency per operation")
                .tag("op", op) // reserve_cash / unreserve_cash / apply_fill / write_history ...
                .publishPercentileHistogram()
                .register(registry);

        timer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void incrementDuplicate(String op) {
        Counter c = Counter.builder("account_db_duplicate_total")
                .description("Duplicate key / request handling")
                .tag("op", op)
                .register(registry);
        c.increment();
    }

    public void incrementInsufficient(String op) {
        Counter c = Counter.builder("account_db_insufficient_total")
                .description("Insufficient funds/position cases")
                .tag("op", op)
                .register(registry);
        c.increment();
    }

    public void incrementError(String op) {
        Counter c = Counter.builder("account_db_error_total")
                .description("DB errors per operation")
                .tag("op", op)
                .register(registry);
        c.increment();
    }
}
