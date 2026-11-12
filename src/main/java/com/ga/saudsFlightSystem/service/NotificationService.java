package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.client.NotificationServiceClient;
import com.ga.saudsFlightSystem.model.request.NotificationRequest;
import com.ga.saudsFlightSystem.model.request.EmailRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final Logger logger = Logger.getLogger(NotificationService.class.getName());
    private final NotificationServiceClient notificationServiceClient;
    @Value("${notification.service-key:${NOTIFICATION_SERVICE_KEY:}}")
    private String serviceKey;

    public void sendToUser(Long userId, String eventName, Object data) {
        afterCommit(() -> deliver(new NotificationRequest(userId, null, eventName, data)));
    }

    public void sendToRole(String role, String eventName, Object data) {
        afterCommit(() -> deliver(new NotificationRequest(null, role, eventName, data)));
    }

    public void sendEmail(String recipient, String subject, String body) {
        EmailRequest request = new EmailRequest(recipient, subject, body);
        afterCommit(() -> deliverEmail(request, false));
    }

    public void sendAsyncEmail(String recipient, String subject, String body) {
        EmailRequest request = new EmailRequest(recipient, subject, body);
        afterCommit(() -> deliverEmail(request, true));
    }

    // Only Aviation knows whether its business transaction committed.
    private void afterCommit(Runnable delivery) {
        if (TransactionSynchronizationManager.isActualTransactionActive() && TransactionSynchronizationManager.isSynchronizationActive()) {
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

    private void deliver(NotificationRequest request) {
        try {
            if (serviceKey.isBlank()) throw new IllegalStateException("Notification service key is missing.");
            notificationServiceClient.send(serviceKey, request);
        } catch (RuntimeException ex) {
            // Delivery is best effort; a failed HTTP call must not fail a committed operation.
            logger.warning("Notification " + request.eventName() + " was not delivered. Check Notification availability and service-key configuration.");
        }
    }

    private void deliverEmail(EmailRequest request, boolean async) {
        try {
            if (serviceKey.isBlank()) throw new IllegalStateException("Notification service key is missing.");
            if (async) notificationServiceClient.sendAsyncEmail(serviceKey, request);
            else notificationServiceClient.sendEmail(serviceKey, request);
        } catch (RuntimeException ex) {
            logger.warning("Email was not delivered. Check Notification availability, SMTP, and service-key configuration.");
        }
    }
}
