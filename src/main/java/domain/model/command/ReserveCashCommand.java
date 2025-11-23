package domain.model.command;

import java.math.BigDecimal;

public record ReserveCashCommand(
        long accountId,
        BigDecimal amount,
        String requestId,
        String orderId
) {}
