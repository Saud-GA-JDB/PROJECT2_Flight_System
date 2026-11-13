package com.ga.notification.consumer;

import com.ga.common.events.NotificationEvent;
import com.ga.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KafkaConsumer {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumer.class);
    private static final int MAX_PROCESSED_EVENTS = 10_000;

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final Set<String> processedEventIds = new LinkedHashSet<>();

    @KafkaListener(topics = "aviation-notification-topic", groupId = "notification-group")
    public void consume(String message) {
        NotificationEvent event = readNotification(message);
        processNotification(event);
    }

    private NotificationEvent readNotification(String message) {
        try {
            return objectMapper.readValue(message, NotificationEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid notification JSON.");
        }
    }

    private void processNotification(NotificationEvent event) {
        validateEvent(event);
        if (processedEventIds.contains(event.eventId())) return;

        if (event.userId() != null) {
            notificationService.sendToUser(event.userId(), event.eventName(), event.data());
        } else {
            notificationService.sendToRole(event.role(), event.eventName(), event.data());
        }

        processedEventIds.add(event.eventId());
        if (processedEventIds.size() > MAX_PROCESSED_EVENTS) {
            var iterator = processedEventIds.iterator();
            iterator.next();
            iterator.remove();
        }
        logger.info("Notification dispatched: eventId={}, eventName={}", event.eventId(), event.eventName());
    }

    private void validateEvent(NotificationEvent event) {
        if (event == null
                || event.eventId() == null || event.eventId().isBlank()
                || event.eventName() == null || event.eventName().isBlank()
                || event.data() == null) {
            throw new IllegalArgumentException("Notification requires an event ID, event name, and data.");
        }
        if ((event.userId() == null) == (event.role() == null)) {
            throw new IllegalArgumentException("Provide either a user ID or a role.");
        }
        if (event.userId() != null && event.userId() <= 0) {
            throw new IllegalArgumentException("User ID must be positive.");
        }
        if (event.role() != null && !"FAA_ADMIN".equals(event.role())) {
            throw new IllegalArgumentException("Unsupported notification role.");
        }
    }
}
