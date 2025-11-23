package util;

import java.math.BigDecimal;

public final class MoneyParser {
    private MoneyParser() {}

    public static BigDecimal parseSafe(String s) {
        if (s == null) return BigDecimal.ZERO;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return BigDecimal.ZERO;

        try {
            return new BigDecimal(trimmed);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}

