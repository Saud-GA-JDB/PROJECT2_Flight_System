package com.ga.saudsFlightSystem.support;

import com.ga.common.contract.notification.EmailRequest;

import com.ga.saudsFlightSystem.client.NotificationServiceClient;
import com.ga.common.events.NotificationEvent;
import com.ga.saudsFlightSystem.producer.KafkaProducer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

// Keeps transaction/scoping fixtures in process; this does not test a Kafka broker.
@TestConfiguration
public class NotificationTestConfiguration {
    @Bean
    public FixtureNotificationService fixtureNotificationService() {
        return new FixtureNotificationService();
    }

    @Bean
    @Primary
    public KafkaProducer fixtureKafkaProducer(FixtureNotificationService notifications) {
        KafkaProducer producer = mock(KafkaProducer.class);
        doAnswer(invocation -> {
            NotificationEvent event = invocation.getArgument(0);
            if (event.userId() != null)
                notifications.sendToUser(event.userId(), event.eventName(), event.data());
            else
                notifications.sendToRole(event.role(), event.eventName(), event.data());
            return null;
        }).when(producer).sendNotificationDetails(any(NotificationEvent.class));
        return producer;
    }

    @Bean
    public KafkaAdmin fixtureKafkaAdmin() {
        // Prevent topic creation/network calls in database-focused fixtures.
        return mock(KafkaAdmin.class);
    }

    @Bean
    @Primary
    public NotificationServiceClient fixtureNotificationClient() {
        return new NotificationServiceClient() {
            @Override
            public void sendEmail(String key, EmailRequest request) {}

            @Override
            public void sendAsyncEmail(String key, EmailRequest request) {}
        };
    }

    public static class FixtureNotificationService extends com.ga.notification.service.NotificationService {
        @Override
        public SseEmitter createEmitter() {
            return super.createEmitter();
        }
    }
}
