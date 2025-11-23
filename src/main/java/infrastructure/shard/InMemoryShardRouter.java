package infrastructure.shard;

import infrastructure.metrics.ShardMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class InMemoryShardRouter {

    @ConfigProperty(name = "account.shard.count", defaultValue = "16")
    int numShards;

    @Inject ShardMetrics metrics;

    private ShardExecutor[] executors;

    @PostConstruct
    void init() {
        executors = new ShardExecutor[numShards];
        for (int i = 0; i < numShards; i++) {
            executors[i] = new ShardExecutor(metrics);
        }
    }

    public ShardExecutor route(long accountId) {
        int idx = (int)(Math.floorMod(accountId, numShards));
        return executors[idx];
    }
}