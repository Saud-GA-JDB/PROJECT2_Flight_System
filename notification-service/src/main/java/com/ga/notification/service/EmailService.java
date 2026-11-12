package com.ga.notification.service;

import com.ga.notification.model.request.EmailRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class EmailService {
    private static final Logger logger = Logger.getLogger(EmailService.class.getName());
    private final ExecutorService applicationExecutor;
    private final JavaMailSender javaMailSender;
    private final List<EmailRequest> failedEmails = new ArrayList<>();
    private final ReentrantLock failedEmailsLock = new ReentrantLock();
    @Value("${spring.mail.username}")
    private String sender;

    public void sendEmail(EmailRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(request.recipient());
        message.setSubject(request.subject());
        message.setText(request.body());
        javaMailSender.send(message);
    }

    // The caller performs its after-commit handoff before invoking this endpoint.
    public void sendAsyncEmail(EmailRequest request) {
        try {
            applicationExecutor.execute(() -> {
                try {
                    sendEmail(request);
                } catch (RuntimeException ex) {
                    logger.warning("Background email failed; retained for retry.");
                    saveFailedEmail(request);
                }
            });
        } catch (RejectedExecutionException ex) {
            logger.warning("Email task could not start; retained for retry.");
            saveFailedEmail(request);
        }
    }

    private void saveFailedEmail(EmailRequest request) {
        failedEmailsLock.lock();
        try {
            failedEmails.add(request);
        } finally {
            failedEmailsLock.unlock();
        }
    }

    @Scheduled(initialDelay = 3 * 60 * 60 * 1000L, fixedDelay = 3 * 60 * 60 * 1000L)
    public void retryFailedEmails() {
        List<EmailRequest> pending;
        failedEmailsLock.lock();
        try {
            pending = new ArrayList<>(failedEmails);
            failedEmails.clear();
        } finally {
            failedEmailsLock.unlock();
        }
        for (EmailRequest request : pending) sendAsyncEmail(request);
    }
}
