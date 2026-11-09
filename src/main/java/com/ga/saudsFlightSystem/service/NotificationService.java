package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.security.CurrentActor;
import com.ga.saudsFlightSystem.model.User;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;

@Service
public class NotificationService {
    private static final Logger logger = Logger.getLogger(NotificationService.class.getName());
    private record Connection(Long userId, String role, SseEmitter emitter) {}

    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();

    /**
     * Opens a notification stream and removes its connection when it ends.
     *
     * @param actor authenticated caller receiving notifications
     * @return the notification stream
     * @throws IOException if the first connection message cannot be sent
     */
    public SseEmitter subscribe(CurrentActor actor) throws IOException {
        SseEmitter emitter = createEmitter();
        Connection connection = new Connection(actor.userId(), actor.role(), emitter);
        emitter.onCompletion(() -> connections.remove(connection));
        emitter.onError(error -> connections.remove(connection));
        emitter.onTimeout(() -> {
            connections.remove(connection);
            emitter.complete();
        });
        connections.add(connection);
        try {
            emitter.send(SseEmitter.event().name("connected").reconnectTime(3000)
                    .data("Notification stream connected"));
        } catch (IOException | IllegalStateException error) {
            connections.remove(connection);
            throw error;
        }
        return emitter;
    }

    /** Compatibility for existing fixtures; HTTP callers pass a CurrentActor. */
    @Deprecated
    public SseEmitter subscribe(User user) throws IOException {
        return subscribe(new CurrentActor(user.getId(), user.getRole().name(),
                user.getStatus() == null ? null : user.getStatus().name(), user.isActive()));
    }

    SseEmitter createEmitter() {
        // Reconnecting periodically re-runs JWT authentication and account checks.
        return new SseEmitter(300_000L);
    }

    public void sendToUser(Long userId, String eventName, Object data) {
        afterCommit(() -> send(c -> c.userId().equals(userId), eventName, data));
    }

    public void sendToRole(User.Role role, String eventName, Object data) {
        afterCommit(() -> send(c -> role.name().equals(c.role()), eventName, data));
    }

    // wait for the transaction to succeed so users do not receive notifications for rolled back changes
    private void afterCommit(Runnable delivery) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delivery.run();
                }
            });
        } else {
            delivery.run();
        }
    }

    private void send(Predicate<Connection> recipient, String eventName, Object data) {
        for (Connection connection : connections) {
            if (recipient.test(connection)) {
                deliver(connection, SseEmitter.event().name(eventName).data(data));
            }
        }
    }

    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        for (Connection connection : connections) {
            deliver(connection, SseEmitter.event().comment("keep-alive"));
        }
    }

    private void deliver(Connection connection, SseEmitter.SseEventBuilder event) {
        try {
            connection.emitter().send(event);
        } catch (IOException error) {
            // The servlet container completes failed network writes.
            connections.remove(connection);
        } catch (RuntimeException error) {
            connections.remove(connection);
            try {
                connection.emitter().completeWithError(error);
            } catch (RuntimeException completionError) {
                logger.warning("Could not complete a failed notification connection");
            }
        }
    }

    @PreDestroy
    public void closeConnections() {
        connections.forEach(connection -> connection.emitter().complete());
        connections.clear();
    }
}
