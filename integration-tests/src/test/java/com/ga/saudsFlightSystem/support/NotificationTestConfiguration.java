package com.ga.saudsFlightSystem.support;

import com.ga.saudsFlightSystem.client.NotificationServiceClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// Keeps the existing transaction/scoping fixtures in process; this does not test HTTP.
@TestConfiguration
public class NotificationTestConfiguration {
    @Bean
    public FixtureNotificationService fixtureNotificationService() {
        return new FixtureNotificationService();
    }

    @Bean
    @Primary
    public NotificationServiceClient fixtureNotificationClient(FixtureNotificationService notifications) {
        return (key, request) -> {
            if (request.userId() != null)
                notifications.sendToUser(request.userId(), request.eventName(), request.data());
            else
                notifications.sendToRole(request.role(), request.eventName(), request.data());
        };
    }

    public static class FixtureNotificationService extends com.ga.notification.service.NotificationService {
        @Override
        public SseEmitter createEmitter() {
            return super.createEmitter();
        }
    }
}
