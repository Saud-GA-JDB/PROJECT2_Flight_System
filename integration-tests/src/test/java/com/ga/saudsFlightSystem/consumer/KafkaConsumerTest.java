package com.ga.saudsFlightSystem.consumer;

import com.ga.common.events.NotificationEvent;
import com.ga.common.security.CurrentActor;
import com.ga.notification.consumer.KafkaConsumer;
import com.ga.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KafkaConsumerTest {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final NotificationService notifications = mock(NotificationService.class);
    private final KafkaConsumer consumer = new KafkaConsumer(notifications, mapper);

    @Test
    void userEventKeepsItsRecipientNameAndStringPayload() {
        consumer.consume(message("event-1", 25L, null, "FLIGHT_CREATED", "Flight GF25 added"));
        verify(notifications).sendToUser(25L, "FLIGHT_CREATED", "Flight GF25 added");
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void roleEventPreservesStructuredJson() {
        Map<String, Object> payload = Map.of("requestId", 12, "status", "PENDING",
                "requestedAt", "2026-11-13T14:00:00");
        consumer.consume(message("event-1", null, "FAA_ADMIN", "airplane-activation-requested", payload));
        verify(notifications).sendToRole("FAA_ADMIN", "airplane-activation-requested", payload);
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void repeatedEventIdDispatchesOnceButNewEventIdStillDispatches() {
        String first = message("event-1", 25L, null, "FLIGHT_CREATED", "Flight GF25 added");
        consumer.consume(first);
        consumer.consume(first);
        consumer.consume(message("event-2", 25L, null, "FLIGHT_CREATED", "Flight GF25 added"));
        verify(notifications, times(2)).sendToUser(25L, "FLIGHT_CREATED", "Flight GF25 added");
    }

    @Test
    void failedDispatchIsNotRememberedAndCanBeRetried() {
        doThrow(new IllegalStateException("Temporary failure")).doNothing()
                .when(notifications).sendToUser(25L, "FLIGHT_CREATED", "Flight GF25 added");
        String message = message("event-1", 25L, null, "FLIGHT_CREATED", "Flight GF25 added");
        assertThrows(IllegalStateException.class, () -> consumer.consume(message));
        consumer.consume(message);
        consumer.consume(message);
        verify(notifications, times(2)).sendToUser(25L, "FLIGHT_CREATED", "Flight GF25 added");
    }

    @ParameterizedTest
    @MethodSource("invalidMessages")
    void invalidMessagesNeverReachSse(String message) {
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(message));
        verifyNoInteractions(notifications);
    }

    static Stream<String> invalidMessages() {
        JsonMapper mapper = JsonMapper.builder().build();
        return Stream.concat(Stream.of("not-json-private-payload", "null", "{}"), Stream.of(
                new NotificationEvent(null, 25L, null, "event", "data"),
                new NotificationEvent(" ", 25L, null, "event", "data"),
                new NotificationEvent("id", 25L, null, " ", "data"),
                new NotificationEvent("id", 25L, null, "event", null),
                new NotificationEvent("id", null, null, "event", "data"),
                new NotificationEvent("id", 25L, "FAA_ADMIN", "event", "data"),
                new NotificationEvent("id", 0L, null, "event", "data"),
                new NotificationEvent("id", null, "AIRLINE_EMPLOYEE", "event", "data")
        ).map(mapper::writeValueAsString));
    }

    @Test
    void malformedJsonExceptionDoesNotExposeThePayload() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> consumer.consume("not-json-private-payload"));
        assertEquals("Invalid notification JSON.", failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test
    void roleEventReachesBothConnectedAdminsAndNoUnrelatedUser() throws IOException {
        SseEmitter firstAdmin = mock(SseEmitter.class);
        SseEmitter secondAdmin = mock(SseEmitter.class);
        SseEmitter unrelated = mock(SseEmitter.class);
        Queue<SseEmitter> emitters = new ArrayDeque<>();
        emitters.add(firstAdmin);
        emitters.add(secondAdmin);
        emitters.add(unrelated);
        NotificationService liveNotifications = new NotificationService() {
            @Override
            protected SseEmitter createEmitter() { return emitters.remove(); }
        };
        try {
            liveNotifications.subscribe(new CurrentActor(1L, "FAA_ADMIN", "ACTIVE", true));
            liveNotifications.subscribe(new CurrentActor(2L, "FAA_ADMIN", "ACTIVE", true));
            liveNotifications.subscribe(new CurrentActor(3L, "AIRLINE_EMPLOYEE", "ACTIVE", true));
            clearInvocations(firstAdmin, secondAdmin, unrelated);

            new KafkaConsumer(liveNotifications, mapper).consume(
                    message("event-1", null, "FAA_ADMIN", "airplane-activation-requested", "Request 12"));

            ArgumentCaptor<SseEmitter.SseEventBuilder> event = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
            verify(firstAdmin).send(event.capture());
            verify(secondAdmin).send(any(SseEmitter.SseEventBuilder.class));
            verify(unrelated, never()).send(any(SseEmitter.SseEventBuilder.class));
            assertTrue(event.getValue().build().stream().anyMatch(part ->
                    part.getData() instanceof String text && text.contains("event:airplane-activation-requested")));
            assertTrue(event.getValue().build().stream().anyMatch(part -> "Request 12".equals(part.getData())));
        } finally {
            liveNotifications.closeConnections();
        }
    }

    @Test
    void noConnectedUsersStillCompletesTheLiveDispatch() {
        KafkaConsumer liveConsumer = new KafkaConsumer(new NotificationService(), mapper);
        assertDoesNotThrow(() -> liveConsumer.consume(
                message("event-1", null, "FAA_ADMIN", "airplane-activation-requested", "Request 12")));
    }

    private String message(String id, Long userId, String role, String name, Object data) {
        return mapper.writeValueAsString(new NotificationEvent(id, userId, role, name, data));
    }
}
