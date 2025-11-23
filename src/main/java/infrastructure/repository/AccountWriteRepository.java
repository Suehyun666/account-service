package infrastructure.repository;

import com.hts.generated.grpc.AccoutResult;
import domain.model.snapshot.AccountSnapshot;
import domain.model.AccountWriteResult;
import domain.model.ServiceResult;
import infrastructure.metrics.DbMetrics;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

import java.math.BigDecimal;

@ApplicationScoped
public class AccountWriteRepository {

    @Inject DSLContext dsl;
    @Inject DbMetrics metrics;

    public AccountWriteResult reserveCash(long accountId, String requestId, String orderId, BigDecimal amount) {
        long startNanos = System.nanoTime();
        try {
            AccountWriteResult result = dsl.transactionResult(cfg -> {
                DSLContext tx = cfg.dsl();

                try {
                    tx.execute(
                        "INSERT INTO account_ledger (account_id, entry_type, request_id, order_id, amount) " +
                        "VALUES (?, 'RESERVE', ?, ?, ?)",
                        accountId, requestId, orderId, amount
                    );
                } catch (DataAccessException e) {
                    if (isDuplicate(e)) {
                        metrics.incrementDuplicate("reserve_cash");
                        return AccountWriteResult.of(ServiceResult.success(), null);
                    }
                    metrics.incrementError("reserve_cash");
                    return AccountWriteResult.of(ServiceResult.of(AccoutResult.INTERNAL_ERROR), null);
                }

                Record rec = tx.fetchOne(
                    "UPDATE accounts " +
                    "SET balance = balance - ?, reserved = reserved + ?, updated_at = now() " +
                    "WHERE account_id = ? AND balance >= ? " +
                    "RETURNING account_id, account_no, balance, reserved, currency, status",
                    amount, amount, accountId, amount
                );

                if (rec == null) {
                    tx.rollback();
                    metrics.incrementInsufficient("reserve_cash");
                    return AccountWriteResult.of(ServiceResult.of(AccoutResult.INSUFFICIENT_FUNDS), null);
                }

                AccountSnapshot snapshot = new AccountSnapshot(
                        rec.get("account_id", Long.class),
                        rec.get("account_no", String.class),
                        rec.get("balance", BigDecimal.class),
                        rec.get("reserved", BigDecimal.class),
                        rec.get("currency", String.class),
                        rec.get("status", String.class)
                );

                return AccountWriteResult.of(ServiceResult.success(), snapshot);
            });

            long durationNanos = System.nanoTime() - startNanos;
            metrics.recordWrite("reserve_cash", durationNanos);
            return result;
        } catch (Exception e) {
            long durationNanos = System.nanoTime() - startNanos;
            metrics.recordWrite("reserve_cash", durationNanos);
            metrics.incrementError("reserve_cash");
            return AccountWriteResult.of(ServiceResult.of(AccoutResult.INTERNAL_ERROR), null);
        }
    }

    public AccountWriteResult unreserveCash(long accountId, String requestId) {
        long startNanos = System.nanoTime();
        try {
            AccountWriteResult result = dsl.transactionResult(cfg -> {
                DSLContext tx = cfg.dsl();

                var queryResult = tx.fetchOne(
                    "SELECT amount, order_id FROM account_ledger " +
                    "WHERE account_id = ? AND request_id = ? AND entry_type = 'RESERVE'",
                    accountId, requestId
                );

                if (queryResult == null) {
                    return AccountWriteResult.of(ServiceResult.of(AccoutResult.ACCOUNT_NOT_FOUND), null);
                }

                BigDecimal amount = queryResult.get("amount", BigDecimal.class);
                String orderId = queryResult.get("order_id", String.class);

                String unreserveRequestId = "un:" + requestId;
                try {
                    tx.execute(
                        "INSERT INTO account_ledger (account_id, entry_type, request_id, order_id, amount) " +
                        "VALUES (?, 'UNRESERVE', ?, ?, ?)",
                        accountId, unreserveRequestId, orderId, amount
                    );
                } catch (DataAccessException e) {
                    if (isDuplicate(e)) {
                        metrics.incrementDuplicate("unreserve_cash");
                        return AccountWriteResult.of(ServiceResult.success(), null);
                    }
                    metrics.incrementError("unreserve_cash");
                    return AccountWriteResult.of(ServiceResult.of(AccoutResult.INTERNAL_ERROR), null);
                }

                var rec = tx.fetchOne(
                    "UPDATE accounts " +
                    "SET reserved = reserved - ?, balance = balance + ?, updated_at = now() " +
                    "WHERE account_id = ? AND reserved >= ? " +
                    "RETURNING account_id, account_no, balance, reserved, currency, status",
                    amount, amount, accountId, amount
                );

                if (rec == null) {
                    tx.rollback();
                    metrics.incrementInsufficient("unreserve_cash");
                    return AccountWriteResult.of(ServiceResult.of(AccoutResult.INSUFFICIENT_FUNDS), null);
                }

                AccountSnapshot snapshot = new AccountSnapshot(
                        rec.get("account_id", Long.class),
                        rec.get("account_no", String.class),
                        rec.get("balance", BigDecimal.class),
                        rec.get("reserved", BigDecimal.class),
                        rec.get("currency", String.class),
                        rec.get("status", String.class)
                );

                return AccountWriteResult.of(ServiceResult.success(), snapshot);
            });

            long durationNanos = System.nanoTime() - startNanos;
            metrics.recordWrite("unreserve_cash", durationNanos);
            return result;
        } catch (Exception e) {
            long durationNanos = System.nanoTime() - startNanos;
            metrics.recordWrite("unreserve_cash", durationNanos);
            metrics.incrementError("unreserve_cash");
            return AccountWriteResult.of(ServiceResult.of(AccoutResult.INTERNAL_ERROR), null);
        }
    }

    public boolean isEventProcessed(String eventId) {
        long startNanos = System.nanoTime();
        try {
            var result = dsl.fetchOne(
                "SELECT 1 FROM processed_events WHERE event_id = ?",
                eventId
            );
            metrics.recordWrite("is_event_processed", System.nanoTime() - startNanos);
            return result != null;
        } catch (Exception e) {
            metrics.recordWrite("is_event_processed", System.nanoTime() - startNanos);
            metrics.incrementError("is_event_processed");
            return false;
        }
    }

    public boolean markEventProcessed(String eventId, String eventType, long accountId) {
        long startNanos = System.nanoTime();
        try {
            dsl.execute(
                "INSERT INTO processed_events (event_id, event_type, account_id) VALUES (?, ?, ?)",
                eventId, eventType, accountId
            );
            metrics.recordWrite("mark_event_processed", System.nanoTime() - startNanos);
            return true;
        } catch (DataAccessException e) {
            metrics.recordWrite("mark_event_processed", System.nanoTime() - startNanos);
            if (isDuplicate(e)) {
                metrics.incrementDuplicate("mark_event_processed");
                return false;
            }
            metrics.incrementError("mark_event_processed");
            return false;
        }
    }

    private boolean isDuplicate(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof org.postgresql.util.PSQLException ex) {
                if ("23505".equals(ex.getSQLState())) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    public boolean createAccount(long accountId) {
        long startNanos = System.nanoTime();
        try {
            int count = dsl.execute(
                "INSERT INTO accounts (account_id, account_no, balance, reserved, currency, status) " +
                "VALUES (?, ?, 0, 0, 'KRW', 'ACTIVE')",
                accountId, "ACC" + accountId
            );
            metrics.recordWrite("create_account", System.nanoTime() - startNanos);
            return count > 0;
        } catch (DataAccessException e) {
            metrics.recordWrite("create_account", System.nanoTime() - startNanos);
            if (isDuplicate(e)) {
                metrics.incrementDuplicate("create_account");
                return false;
            }
            metrics.incrementError("create_account");
            throw e;
        }
    }

    public boolean deleteAccount(long accountId) {
        long startNanos = System.nanoTime();
        try {
            int count = dsl.execute(
                "DELETE FROM accounts WHERE account_id = ?",
                accountId
            );
            metrics.recordWrite("delete_account", System.nanoTime() - startNanos);
            return count > 0;
        } catch (Exception e) {
            metrics.recordWrite("delete_account", System.nanoTime() - startNanos);
            metrics.incrementError("delete_account");
            throw e;
        }
    }

    public AccountWriteResult updateAccountStatus(long accountId, String status) {
        long startNanos = System.nanoTime();
        try {
            Record rec = dsl.fetchOne(
                "UPDATE accounts SET status = ?, updated_at = now() " +
                "WHERE account_id = ? " +
                "RETURNING account_id, account_no, balance, reserved, currency, status",
                status, accountId
            );

            if (rec == null) {
                metrics.recordWrite("update_account_status", System.nanoTime() - startNanos);
                return AccountWriteResult.of(ServiceResult.of(AccoutResult.ACCOUNT_NOT_FOUND), null);
            }

            AccountSnapshot snapshot = new AccountSnapshot(
                    rec.get("account_id", Long.class),
                    rec.get("account_no", String.class),
                    rec.get("balance", BigDecimal.class),
                    rec.get("reserved", BigDecimal.class),
                    rec.get("currency", String.class),
                    rec.get("status", String.class)
            );

            metrics.recordWrite("update_account_status", System.nanoTime() - startNanos);
            return AccountWriteResult.of(ServiceResult.success(), snapshot);
        } catch (Exception e) {
            metrics.recordWrite("update_account_status", System.nanoTime() - startNanos);
            metrics.incrementError("update_account_status");
            return AccountWriteResult.of(ServiceResult.of(AccoutResult.INTERNAL_ERROR), null);
        }
    }
}
