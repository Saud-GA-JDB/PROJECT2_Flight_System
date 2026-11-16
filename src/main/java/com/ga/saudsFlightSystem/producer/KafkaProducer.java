package com.ga.saudsFlightSystem.producer;

import com.ga.common.events.NotificationEvent;
import com.ga.common.events.NotificationTopics;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class KafkaProducer {
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendNotificationDetails(NotificationEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            String key = event.userId() != null
                    ? "user:" + event.userId()
                    : "role:" + event.role();

            kafkaTemplate.send(NotificationTopics.AVIATION_NOTIFICATIONS, key, message)
                    .whenComplete((result, exception) -> {
                        if (exception == null) {
                            logger.info("Notification published: eventId={}, eventName={}",
                                    event.eventId(), event.eventName());
                        } else {
                            logger.error("Notification publish failed: eventId={}, eventName={}, error={}",
                                    event.eventId(), event.eventName(), exception.getClass().getSimpleName());
                        }
                    });
        } catch (RuntimeException exception) {
            logger.error("Notification could not be published: eventId={}, eventName={}, error={}",
                    event.eventId(), event.eventName(), exception.getClass().getSimpleName());
        }
    }
}
