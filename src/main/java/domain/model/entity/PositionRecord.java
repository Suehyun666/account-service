package domain.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PositionRecord(
        long positionId,
        long accountId,
        String symbol,
        BigDecimal quantity,
        BigDecimal avgPrice,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}