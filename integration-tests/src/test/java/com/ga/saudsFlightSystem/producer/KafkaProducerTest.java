package com.ga.saudsFlightSystem.producer;

import com.ga.common.events.NotificationEvent;
import com.ga.saudsFlightSystem.model.request.AirplaneRequest;
import com.ga.saudsFlightSystem.model.request.response.AirplaneRequestResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class KafkaProducerTest {
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final KafkaProducer producer = new KafkaProducer(template, mapper);

    @Test
    void roleEventPreservesAirplanePayloadAndLogsSuccessOnlyAfterAcknowledgment(CapturedOutput output) {
        CompletableFuture<SendResult<String, String>> pending = new CompletableFuture<>();
        when(template.send(anyString(), anyString(), anyString())).thenReturn(pending);
        AirplaneRequestResponse payload = new AirplaneRequestResponse(
                12L, 8L, "A9C-EXAMPLE", "Airbus A320", AirplaneRequest.ApprovalStatus.PENDING,
                null, LocalDateTime.of(2026, 11, 13, 14, 0), null);
        NotificationEvent event = new NotificationEvent(
                "role-event-id", null, "FAA_ADMIN", "airplane-activation-requested", payload);

        producer.sendNotificationDetails(event);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(template).send(eq("aviation-notification-topic"), eq("role:FAA_ADMIN"), message.capture());
        var json = mapper.readTree(message.getValue());
        assertEquals(event.eventId(), json.get("eventId").asString());
        assertTrue(json.get("userId").isNull());
        assertEquals("FAA_ADMIN", json.get("role").asString());
        assertEquals(event.eventName(), json.get("eventName").asString());
        assertEquals(mapper.readTree(mapper.writeValueAsString(payload)), json.get("data"));
        assertEquals("2026-11-13T14:00:00", json.get("data").get("requestedAt").asString());
        assertFalse(output.getAll().contains("Notification published:"));

        pending.complete(null);
        assertTrue(output.getAll().contains("Notification published: eventId=role-event-id"));
        assertFalse(output.getAll().contains("A9C-EXAMPLE"));
    }

    @Test
    void userEventUsesUserKeyAndPreservesAStringPayload() {
        when(template.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        producer.sendNotificationDetails(new NotificationEvent(
                "user-event-id", 25L, null, "FLIGHT_CREATED", "Flight GF25 added"));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(template).send(eq("aviation-notification-topic"), eq("user:25"), message.capture());
        NotificationEvent decoded = mapper.readValue(message.getValue(), NotificationEvent.class);
        assertEquals(25L, decoded.userId());
        assertNull(decoded.role());
        assertEquals("Flight GF25 added", decoded.data());
    }

    @Test
    void asynchronousFailureLogsIdentifiersWithoutPayloadOrSuccess(CapturedOutput output) {
        CompletableFuture<SendResult<String, String>> pending = new CompletableFuture<>();
        when(template.send(anyString(), anyString(), anyString())).thenReturn(pending);
        producer.sendNotificationDetails(new NotificationEvent(
                "failed-event-id", 25L, null, "FLIGHT_CREATED", "private-payload"));

        pending.completeExceptionally(new IllegalStateException("private-exception-detail"));

        assertTrue(output.getAll().contains("Notification publish failed: eventId=failed-event-id"));
        assertFalse(output.getAll().contains("Notification published:"));
        assertFalse(output.getAll().contains("private-payload"));
        assertFalse(output.getAll().contains("private-exception-detail"));
        verify(template, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void serializationFailureDoesNotSendOrEscape(CapturedOutput output) {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        NotificationEvent event = new NotificationEvent(
                "invalid-event-id", 25L, null, "FLIGHT_CREATED", "private-payload");
        when(failingMapper.writeValueAsString(event)).thenThrow(new IllegalArgumentException("private-payload"));

        assertDoesNotThrow(() -> new KafkaProducer(template, failingMapper).sendNotificationDetails(event));

        verifyNoInteractions(template);
        assertTrue(output.getAll().contains("Notification could not be published: eventId=invalid-event-id"));
        assertFalse(output.getAll().contains("private-payload"));
    }
}
