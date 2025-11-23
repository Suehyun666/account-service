package infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.grpc.Status;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class GrpcMetrics {

    private final MeterRegistry registry;

    @Inject
    public GrpcMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRequest(String method, Status status, long durationNanos) {
        String codeGroup = classifyStatus(status);

        // Counter
        Counter counter = Counter.builder("account_grpc_requests_total")
                .description("Total number of gRPC requests for account service")
                .tag("method", method)
                .tag("code_group", codeGroup) // OK / CLIENT_ERROR / SERVER_ERROR
                .register(registry);
        counter.increment();

        // Latency
        Timer timer = Timer.builder("account_grpc_latency_seconds")
                .description("gRPC server latency for account service")
                .tag("method", method)
                .tag("code_group", codeGroup)
                .publishPercentileHistogram()
                .register(registry);
        timer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private String classifyStatus(Status status) {
        Status.Code code = status.getCode();
        return switch (code) {
            case OK -> "OK";
            case INVALID_ARGUMENT, FAILED_PRECONDITION, OUT_OF_RANGE, UNAUTHENTICATED, PERMISSION_DENIED, NOT_FOUND -> "CLIENT_ERROR";
            default -> "SERVER_ERROR";
        };
    }
}
