package domain.model.snapshot;

import java.math.BigDecimal;

public record PositionSnapshot(
        long accountId,
        String symbol,
        BigDecimal quantity,
        BigDecimal avgPrice
) {
    public PositionSnapshot {
        if (accountId < 0) accountId = 0;
        symbol = (symbol == null || symbol.isBlank()) ? "UNKNOWN" : symbol;
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
        avgPrice = avgPrice == null ? BigDecimal.ZERO : avgPrice;
    }
}
