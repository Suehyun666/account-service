package domain.model.snapshot;

import java.math.BigDecimal;

public record AccountSnapshot(
        long accountId,
        String accountNo,
        BigDecimal balance,
        BigDecimal reserved,
        String currency,
        String status
) {
    public AccountSnapshot {
        if (accountId < 0) accountId = 0;
        accountNo = (accountNo == null || accountNo.isBlank()) ? "UNKNOWN" : accountNo;
        balance = balance == null ? BigDecimal.ZERO : balance;
        reserved = reserved == null ? BigDecimal.ZERO : reserved;
        currency = (currency == null || currency.isBlank()) ? "KRW" : currency;
        status = (status == null || status.isBlank()) ? "ACTIVE" : status;
    }
}
