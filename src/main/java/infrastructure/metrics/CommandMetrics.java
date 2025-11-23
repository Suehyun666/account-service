package infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class CommandMetrics {

    private final MeterRegistry registry;

    @Inject
    public CommandMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String op, String result, long durationNanos) {
        // op: reserve_cash / unreserve_cash / reserve_position / unreserve_position
        // result: SUCCESS / INSUFFICIENT / DUPLICATE / FAILURE

        Counter counter = Counter.builder("account_command_total")
                .description("Total command executions")
                .tag("op", op)
                .tag("result", result)
                .register(registry);
        counter.increment();

        Timer timer = Timer.builder("account_command_latency_seconds")
                .description("Command execution latency")
                .tag("op", op)
                .tag("result", result)
                .publishPercentileHistogram()
                .register(registry);

        timer.record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
