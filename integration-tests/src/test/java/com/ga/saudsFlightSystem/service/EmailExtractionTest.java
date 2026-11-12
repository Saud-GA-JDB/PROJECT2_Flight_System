package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.client.NotificationServiceClient;
import com.ga.saudsFlightSystem.model.request.EmailRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// No application context, database, HTTP, or SMTP. Runtime checks cover the real endpoints.
class EmailExtractionTest {
    private final NotificationServiceClient client = mock(NotificationServiceClient.class);
    private final NotificationService service = new NotificationService(client);

    EmailExtractionTest() {
        ReflectionTestUtils.setField(service, "serviceKey", "test-key");
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    @Test
    void ordinaryAndCrewEmailsWaitForCommit() {
        beginTransaction();
        service.sendEmail("recipient@example.com", "Booking", "Confirmed");
        service.sendAsyncEmail("crew@example.com", "Crew", "Assigned");
        verifyNoInteractions(client);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(client).sendEmail("test-key", new EmailRequest("recipient@example.com", "Booking", "Confirmed"));
        verify(client).sendAsyncEmail("test-key", new EmailRequest("crew@example.com", "Crew", "Assigned"));
    }

    @Test
    void rollbackSendsNeitherEmail() {
        beginTransaction();
        service.sendEmail("recipient@example.com", "Booking", "Confirmed");
        service.sendAsyncEmail("crew@example.com", "Crew", "Assigned");
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(client);
    }

    @Test
    void handoffFailureDoesNotFailCommittedOperationOrRetry() {
        doThrow(new IllegalStateException("offline")).when(client).sendEmail(anyString(), any());
        doThrow(new IllegalStateException("offline")).when(client).sendAsyncEmail(anyString(), any());
        beginTransaction();
        service.sendEmail("recipient@example.com", "Booking", "Confirmed");
        service.sendAsyncEmail("crew@example.com", "Crew", "Assigned");
        assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit));
        verify(client, times(1)).sendEmail(anyString(), any());
        verify(client, times(1)).sendAsyncEmail(anyString(), any());
    }

    @Test
    void missingServiceKeyDoesNotSend() {
        ReflectionTestUtils.setField(service, "serviceKey", "");
        assertDoesNotThrow(() -> service.sendEmail("recipient@example.com", "Booking", "Confirmed"));
        verifyNoInteractions(client);
    }
}
