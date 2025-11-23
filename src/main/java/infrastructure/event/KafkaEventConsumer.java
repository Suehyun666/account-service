package infrastructure.event;

import com.hts.generated.events.LoginEvent;
import infrastructure.repository.AccountWriteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class KafkaEventConsumer {
    @Inject AccountWriteRepository writeRepo;

    private static final Logger log = Logger.getLogger(KafkaEventConsumer.class);

    @Incoming("login-events")
    public CompletionStage<Void> consume(Message<byte[]> message) {
        LoginEvent event;
        try {
            event = LoginEvent.parseFrom(message.getPayload());
        } catch (Exception e) {
            log.errorf(e, "Failed to parse LoginEvent");
            return message.nack(e);
        }

        String eventId = generateEventId(event);

        if (writeRepo.isEventProcessed(eventId)) {
            log.infof("Event already processed: %s", eventId);
            return message.ack();
        }

        try {
            processLoginEvent(event);

            boolean marked = writeRepo.markEventProcessed(eventId, "LOGIN", event.getAccountId());
            if (!marked) {
                log.warnf("Event already processed (concurrent): %s", eventId);
            } else {
                log.infof("Login event processed: accountId=%d sessionId=%d", event.getAccountId(), event.getSessionId());
            }

            return message.ack();
        } catch (Exception e) {
            log.errorf(e, "Failed to process login event: %s", eventId);
            return message.nack(e);
        }
    }

    private void processLoginEvent(LoginEvent event) {

    }

    private String generateEventId(LoginEvent event) {
        return "login:" + event.getAccountId() + ":" + event.getSessionId() + ":" + event.getTimestamp();
    }
}
