package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.client.NotificationServiceClient;
import com.ga.common.events.NotificationEvent;
import com.ga.saudsFlightSystem.model.request.EmailRequest;
import com.ga.saudsFlightSystem.producer.KafkaProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final Logger logger = Logger.getLogger(NotificationService.class.getName());
    private final NotificationServiceClient notificationServiceClient;
    private final KafkaProducer kafkaProducer;
    @Value("${notification.service-key:${NOTIFICATION_SERVICE_KEY:}}")
    private String serviceKey;

    public void sendToUser(Long userId, String eventName, Object data) {
        NotificationEvent event = new NotificationEvent(
                UUID.randomUUID().toString(), userId, null, eventName, data);
        afterCommit(() -> kafkaProducer.sendNotificationDetails(event));
    }

    public void sendToRole(String role, String eventName, Object data) {
        NotificationEvent event = new NotificationEvent(
                UUID.randomUUID().toString(), null, role, eventName, data);
        afterCommit(() -> kafkaProducer.sendNotificationDetails(event));
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
