package com.ga.saudsFlightSystem.service;

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

@Service
public class NotificationService {
    private record Connection(Long userId, User.Role role, SseEmitter emitter) {}

    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe(User user) throws IOException {
        SseEmitter emitter = createEmitter();
        Connection connection = new Connection(user.getId(), user.getRole(), emitter);
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

    SseEmitter createEmitter() {
        // Reconnecting periodically re-runs JWT authentication and account checks.
        return new SseEmitter(300_000L);
    }

    public void sendToUser(Long userId, String eventName, Object data) {
        afterCommit(() -> send(c -> c.userId().equals(userId), eventName, data));
    }

    public void sendToRole(User.Role role, String eventName, Object data) {
        afterCommit(() -> send(c -> c.role() == role, eventName, data));
    }

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
            connection.emitter().completeWithError(error);
        }
    }

    @PreDestroy
    public void closeConnections() {
        connections.forEach(connection -> connection.emitter().complete());
        connections.clear();
    }
}
