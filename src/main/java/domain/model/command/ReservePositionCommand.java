package domain.model.command;

import java.math.BigDecimal;

public record ReservePositionCommand(
        long accountId,
        String symbol,
        BigDecimal quantity,
        String requestId,
        String orderId
) {}
