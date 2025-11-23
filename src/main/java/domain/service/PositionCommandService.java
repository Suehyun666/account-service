package domain.service;

import com.hts.generated.grpc.AccoutResult;
import domain.model.snapshot.PositionSnapshot;
import domain.model.ServiceResult;
import domain.model.command.ReservePositionCommand;
import domain.model.command.UnreservePositionCommand;
import infrastructure.metrics.CommandMetrics;
import infrastructure.repository.PositionWriteRepository;
import infrastructure.repository.RedisPositionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PositionCommandService {

    private static final Logger log = Logger.getLogger(PositionCommandService.class);

    @Inject PositionWriteRepository writeRepo;
    @Inject RedisPositionRepository redis;
    @Inject CommandMetrics metrics;

    public ServiceResult reservePosition(ReservePositionCommand cmd) {
        long startNanos = System.nanoTime();

        PositionSnapshot snapshot =
                writeRepo.reservePosition(cmd.accountId(), cmd.symbol(), cmd.quantity());

        if (snapshot != null) {
            try {
                redis.set(snapshot);
            } catch (Exception e) {
                log.warnf(e, "Failed to update position cache. accountId=%d, symbol=%s",
                        cmd.accountId(), cmd.symbol());
            }

            long durationNanos = System.nanoTime() - startNanos;
            metrics.record("reserve_position", "SUCCESS", durationNanos);
            return ServiceResult.success();
        } else {
            long durationNanos = System.nanoTime() - startNanos;
            metrics.record("reserve_position", "FAILURE", durationNanos);
            return ServiceResult.of(AccoutResult.INTERNAL_ERROR);
        }
    }

    public ServiceResult unreservePosition(UnreservePositionCommand cmd) {
        long startNanos = System.nanoTime();

        PositionSnapshot snapshot = writeRepo.unreservePosition(
                cmd.accountId(), cmd.requestId()
        );

        if (snapshot == null) {
            long durationNanos = System.nanoTime() - startNanos;
            metrics.record("unreserve_position", "INSUFFICIENT", durationNanos);
            return ServiceResult.of(AccoutResult.INSUFFICIENT_FUNDS);
        }

        try {
            redis.set(snapshot);
        } catch (Exception e) {
            log.warnf(e, "Failed to update position cache. accountId=%d",
                    cmd.accountId());
        }

        long durationNanos = System.nanoTime() - startNanos;
        metrics.record("unreserve_position", "SUCCESS", durationNanos);
        return ServiceResult.success();
    }
}
