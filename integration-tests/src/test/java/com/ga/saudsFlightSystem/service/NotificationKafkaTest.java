package com.ga.saudsFlightSystem.service;

import com.ga.common.events.NotificationEvent;
import com.ga.saudsFlightSystem.client.NotificationServiceClient;
import com.ga.saudsFlightSystem.producer.KafkaProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationKafkaTest {
    private final NotificationServiceClient client = mock(NotificationServiceClient.class);
    private final KafkaProducer producer = mock(KafkaProducer.class);
    private final NotificationService service = new NotificationService(client, producer);

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        verifyNoInteractions(client);
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    void userAndRoleEventsWaitForCommitAndKeepTheirAudiences() {
        beginTransaction();
        service.sendToUser(25L, "FLIGHT_CREATED", "Flight GF25 added");
        service.sendToRole("FAA_ADMIN", "airplane-activation-requested", "Request 12");
        verifyNoInteractions(producer);

        commit();

        ArgumentCaptor<NotificationEvent> events = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(producer, times(2)).sendNotificationDetails(events.capture());
        NotificationEvent userEvent = events.getAllValues().get(0);
        NotificationEvent roleEvent = events.getAllValues().get(1);
        assertEquals(25L, userEvent.userId());
        assertNull(userEvent.role());
        assertEquals("FLIGHT_CREATED", userEvent.eventName());
        assertEquals("Flight GF25 added", userEvent.data());
        assertNull(roleEvent.userId());
        assertEquals("FAA_ADMIN", roleEvent.role());
        assertEquals("airplane-activation-requested", roleEvent.eventName());
        assertEquals("Request 12", roleEvent.data());
        assertDoesNotThrow(() -> UUID.fromString(userEvent.eventId()));
        assertDoesNotThrow(() -> UUID.fromString(roleEvent.eventId()));
        assertNotEquals(userEvent.eventId(), roleEvent.eventId());
    }

    @Test
    void rollbackPublishesNothing() {
        beginTransaction();
        service.sendToUser(25L, "FLIGHT_CREATED", "Flight GF25 added");
        service.sendToRole("FAA_ADMIN", "airplane-activation-requested", "Request 12");
        TransactionSynchronizationManager.getSynchronizations().forEach(
                sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(producer);
    }

    @Test
    void alreadyCommittedMaintenanceHandoffPublishesImmediately() {
        service.sendToUser(25L, "Maintenance_Cancelled", "Request cancelled");
        verify(producer).sendNotificationDetails(argThat(event ->
                Long.valueOf(25L).equals(event.userId())
                        && "Maintenance_Cancelled".equals(event.eventName())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void brokerSendFailureDoesNotEscapeTheAfterCommitCallback() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Broker unavailable"));
        NotificationService failingService = new NotificationService(
                client, new KafkaProducer(template, JsonMapper.builder().findAndAddModules().build()));
        beginTransaction();
        failingService.sendToRole("FAA_ADMIN", "airplane-activation-requested", "Request 12");
        verifyNoInteractions(template);

        assertDoesNotThrow(this::commit);
        verify(template).send(eq(KafkaProducer.AVIATION_NOTIFICATION_TOPIC), eq("role:FAA_ADMIN"), anyString());
    }
}
