package infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class EndToEndMetrics {

    private final MeterRegistry registry;

    @Inject
    public EndToEndMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String method, String result, long durationNanos) {
        Timer timer = Timer.builder("account_request_latency_seconds")
                .description("End-to-end latency for account gRPC requests")
                .tag("method", method)
                .tag("result", result) // SUCCESS / INSUFFICIENT / DUPLICATE / FAILURE 등
                .publishPercentileHistogram() // Prometheus histogram으로 export
                .register(registry);

        timer.record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
