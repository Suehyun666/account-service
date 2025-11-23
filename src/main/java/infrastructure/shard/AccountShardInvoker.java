package infrastructure.shard;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AccountShardInvoker {

    @Inject InMemoryShardRouter router;

    public <T> Uni<T> invoke(long accountId, java.util.function.Supplier<T> work) {
        return Uni.createFrom().emitter(em -> {
            ShardExecutor shard = router.route(accountId);
            shard.execute(() -> {
                try {
                    T result = work.get();
                    em.complete(result);
                } catch (Throwable t) {
                    em.fail(t);
                }
            });
        });
    }
}

