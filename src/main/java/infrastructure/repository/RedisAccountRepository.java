package infrastructure.repository;

import domain.model.snapshot.AccountSnapshot;
import infrastructure.metrics.RedisMetrics;
import io.vertx.mutiny.redis.client.RedisAPI;
import io.vertx.mutiny.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.*;

@ApplicationScoped
public class RedisAccountRepository {

    @Inject RedisAPI redis;
    @Inject RedisMetrics metrics;
    private static final String PREFIX = "acc:";

    public AccountSnapshot get(long accountId) {
        long startNanos = System.nanoTime();
        try {
            String key = PREFIX + accountId;
            Response hash = redis.hgetall(key).await().indefinitely();
            AccountSnapshot result = decode(hash);
            metrics.recordGet(System.nanoTime() - startNanos);
            return result;
        } catch (Exception e) {
            metrics.recordGet(System.nanoTime() - startNanos);
            metrics.incrementFailure("get");
            throw e;
        }
    }

    public void set(AccountSnapshot acc) {
        long startNanos = System.nanoTime();
        try {
            String key = PREFIX + acc.accountId();

            List<String> args = List.of(
                    key,
                    "account_id", String.valueOf(acc.accountId()),
                    "account_no", acc.accountNo(),
                    "balance",    acc.balance().toPlainString(),
                    "reserved",   acc.reserved().toPlainString(),
                    "currency",   acc.currency(),
                    "status",     acc.status()
            );

            redis.hset(args).await().indefinitely();
            metrics.recordSet(System.nanoTime() - startNanos);
        } catch (Exception e) {
            metrics.recordSet(System.nanoTime() - startNanos);
            metrics.incrementFailure("set");
            throw e;
        }
    }

    public void delete(long accountId) {
        long startNanos = System.nanoTime();
        try {
            String key = PREFIX + accountId;
            redis.del(List.of(key)).await().indefinitely();
            metrics.recordSet(System.nanoTime() - startNanos);
        } catch (Exception e) {
            metrics.recordSet(System.nanoTime() - startNanos);
            metrics.incrementFailure("delete");
            throw e;
        }
    }

    private AccountSnapshot decode(Response hash) {
        if (hash == null || hash.size() == 0) return null;

        Map<String,String> m = new HashMap<>();
        for (int i = 0; i < hash.size(); i += 2) {
            m.put(hash.get(i).toString(), hash.get(i+1).toString());
        }

        return new AccountSnapshot(
                parseLong(m.get("account_id")),
                safeStr(m.get("account_no")),
                parseDec(m.get("balance")),
                parseDec(m.get("reserved")),
                safeStr(m.get("currency")),
                safeStr(m.get("status"))
        );
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }

    private BigDecimal parseDec(String s) {
        try { return new BigDecimal(s); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private String safeStr(String s) {
        return (s == null || s.isBlank()) ? "" : s;
    }
}