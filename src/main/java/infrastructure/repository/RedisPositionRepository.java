package infrastructure.repository;

import domain.model.snapshot.PositionSnapshot;
import infrastructure.metrics.RedisMetrics;
import io.vertx.mutiny.redis.client.RedisAPI;
import io.vertx.mutiny.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class RedisPositionRepository {

    @Inject RedisAPI redis;
    @Inject RedisMetrics metrics;
    private static final String PREFIX = "pos:";

    public PositionSnapshot get(long accountId, String symbol) {
        long startNanos = System.nanoTime();
        try {
            String key = PREFIX + accountId + ":" + symbol;
            Response hash = redis.hgetall(key).await().indefinitely();
            PositionSnapshot result = decode(hash);
            metrics.recordGet(System.nanoTime() - startNanos);
            return result;
        } catch (Exception e) {
            metrics.recordGet(System.nanoTime() - startNanos);
            metrics.incrementFailure("get");
            throw e;
        }
    }

    public void set(PositionSnapshot pos) {
        long startNanos = System.nanoTime();
        try {
            String key = PREFIX + pos.accountId() + ":" + pos.symbol();

            List<String> args = List.of(
                    key,
                    "account_id", String.valueOf(pos.accountId()),
                    "symbol",     pos.symbol(),
                    "quantity",   pos.quantity().toPlainString(),
                    "avg_price",  pos.avgPrice().toPlainString()
            );

            redis.hset(args).await().indefinitely();
            metrics.recordSet(System.nanoTime() - startNanos);
        } catch (Exception e) {
            metrics.recordSet(System.nanoTime() - startNanos);
            metrics.incrementFailure("set");
            throw e;
        }
    }

    private PositionSnapshot decode(Response hash) {
        if (hash == null || hash.size() == 0) return null;

        Map<String,String> m = new HashMap<>();
        for (int i = 0; i < hash.size(); i += 2) {
            m.put(hash.get(i).toString(), hash.get(i+1).toString());
        }

        return new PositionSnapshot(
                parseLong(m.get("account_id")),
                safeStr(m.get("symbol")),
                parseDec(m.get("quantity")),
                parseDec(m.get("avg_price"))
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
