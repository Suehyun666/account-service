package infrastructure.repository;

import domain.model.snapshot.PositionSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.postgresql.util.PSQLException;

import java.math.BigDecimal;

@ApplicationScoped
public class PositionWriteRepository {

    @Inject DSLContext dsl;

    public PositionSnapshot reservePosition(long accountId, String symbol, BigDecimal qtyChange) {
        return dsl.transactionResult(cfg -> {
            DSLContext tx = cfg.dsl();

            tx.execute(
                "INSERT INTO position_ledger (account_id, symbol, entry_type, request_id, quantity_change, price) " +
                "VALUES (?, ?, 'RESERVE', gen_random_uuid()::text, ?, 0)",
                accountId, symbol, qtyChange
            );

            Record rec = tx.fetchOne(
                    "INSERT INTO positions (account_id, symbol, quantity, avg_price) " +
                    "VALUES (?, ?, ?, 0) " +
                    "ON CONFLICT (account_id, symbol) DO UPDATE " +
                    "SET quantity = positions.quantity + EXCLUDED.quantity, " +
                    "    updated_at = now() " +
                    "RETURNING account_id, symbol, quantity, avg_price",
                    accountId, symbol, qtyChange
            );

            PositionSnapshot snapshot = new PositionSnapshot(
                    rec.get("account_id", Long.class),
                    rec.get("symbol", String.class),
                    rec.get("quantity", BigDecimal.class),
                    rec.get("avg_price", BigDecimal.class)
            );

            return snapshot;
        });
    }

    public PositionSnapshot unreservePosition(long accountId, String reserveId) {

        return dsl.transactionResult(cfg -> {
            DSLContext tx = cfg.dsl();

            // 1) 원본 RESERVE ledger 찾기
            Record reserveRec = tx.fetchOne(
                    "SELECT position_id, symbol, quantity_change, order_id " +
                            "FROM position_ledger " +
                            "WHERE account_id = ? AND request_id = ? AND entry_type = 'RESERVE'",
                    accountId, reserveId
            );

            if (reserveRec == null) {
                return null; // 상위에서 INVALID_REQUEST / NOT_FOUND로 매핑
            }

            Long positionId = reserveRec.get("position_id", Long.class);
            String symbol = reserveRec.get("symbol", String.class);
            BigDecimal reservedQty = reserveRec.get("quantity_change", BigDecimal.class);
            String orderId = reserveRec.get("order_id", String.class);

            if (reservedQty == null || reservedQty.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }

            // 2) UNRESERVE ledger insert (idempotent)
            String unreserveRequestId = "un:" + reserveId;

            try {
                tx.execute(
                        "INSERT INTO position_ledger " +
                                " (account_id, position_id, symbol, entry_type, request_id, order_id, quantity_change, price) " +
                                "VALUES (?, ?, ?, 'UNRESERVE', ?, ?, ?, 0)",
                        accountId,
                        positionId,
                        symbol,
                        unreserveRequestId,
                        orderId,
                        reservedQty.negate() // -qty
                );
            } catch (DataAccessException e) {
                if (isDuplicate(e)) {
                    // 이미 UNRESERVE 한 번 된 상태 → idempotent success로 처리
                    // 이 경우 지금 positions snapshot만 리턴하면 됨
                    Record pos = tx.fetchOne(
                            "SELECT account_id, symbol, quantity, avg_price " +
                                    "FROM positions WHERE account_id = ? AND symbol = ?",
                            accountId, symbol
                    );

                    if (pos == null) {
                        return null;
                    }

                    return new PositionSnapshot(
                            pos.get("account_id", Long.class),
                            pos.get("symbol", String.class),
                            pos.get("quantity", BigDecimal.class),
                            pos.get("avg_price", BigDecimal.class)
                    );
                }
                // 다른 에러면 상위에서 INTERNAL_ERROR로 매핑하도록 null or 예외
                throw e;
            }

            // 3) positions 업데이트
            Record rec = tx.fetchOne(
                    "UPDATE positions " +
                            "SET quantity = quantity - ?, updated_at = now() " +
                            "WHERE account_id = ? AND symbol = ? AND quantity >= ? " +
                            "RETURNING account_id, symbol, quantity, avg_price",
                    reservedQty, accountId, symbol, reservedQty
            );

            if (rec == null) {
                // 수량 부족 / 존재하지 않음
                return null;
            }

            return new PositionSnapshot(
                    rec.get("account_id", Long.class),
                    rec.get("symbol", String.class),
                    rec.get("quantity", BigDecimal.class),
                    rec.get("avg_price", BigDecimal.class)
            );
        });
    }

    private boolean isDuplicate(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof PSQLException ex) {
                if ("23505".equals(ex.getSQLState())) { // unique_violation
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }
}