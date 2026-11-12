package com.ga.saudsFlightSystem.service;

import com.ga.notification.model.request.EmailRequest;
import com.ga.notification.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailSenderTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final ExecutorService executor = mock(ExecutorService.class);
    private final EmailService service = new EmailService(executor, sender);
    private final EmailRequest request = new EmailRequest("recipient@example.com", "Subject", "Body");

    EmailSenderTest() {
        ReflectionTestUtils.setField(service, "sender", "sender@example.com");
        doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; }).when(executor).execute(any());
    }

    @Test
    void sendsOriginalContent() {
        service.sendEmail(request);
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertEquals("sender@example.com", message.getValue().getFrom());
        assertArrayEquals(new String[]{request.recipient()}, message.getValue().getTo());
        assertEquals(request.subject(), message.getValue().getSubject());
        assertEquals(request.body(), message.getValue().getText());
    }

    @Test
    void synchronousFailurePropagatesAndIsNotRetried() {
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(SimpleMailMessage.class));
        assertThrows(MailSendException.class, () -> service.sendEmail(request));
        service.retryFailedEmails();
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
        verifyNoInteractions(executor);
    }

    @Test
    void failedBackgroundEmailIsRetriedAndThenRemoved() {
        doThrow(new MailSendException("SMTP unavailable")).doNothing().when(sender).send(any(SimpleMailMessage.class));
        assertDoesNotThrow(() -> service.sendAsyncEmail(request));
        service.retryFailedEmails();
        service.retryFailedEmails();
        verify(sender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void rejectedTaskIsRetainedForRetry() {
        doThrow(new RejectedExecutionException()).doAnswer(call -> {
            call.getArgument(0, Runnable.class).run(); return null;
        }).when(executor).execute(any());
        service.sendAsyncEmail(request);
        verifyNoInteractions(sender);
        service.retryFailedEmails();
        verify(sender).send(any(SimpleMailMessage.class));
    }
}
