package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.model.Email;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;


@Service
public class EmailService {
    @Autowired
    private ExecutorService applicationExecutor;
    private static final Logger logger = Logger.getLogger(EmailService.class.getName());
    private List<Email> failedEmailsList = new ArrayList<>();
    private ReentrantLock failedEmailsLock = new ReentrantLock();

    @Autowired
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String sender;

    // Send simple mail
    public String sendEmail(String recipientMail, String subject, String body) {
        Email details = new Email(recipientMail, subject, body);
        SimpleMailMessage mailMessage = new SimpleMailMessage();

        mailMessage.setFrom(sender);
        mailMessage.setTo(details.getRecipient());
        mailMessage.setText(details.getMsgBody());
        mailMessage.setSubject(details.getSubject());

        javaMailSender.send(mailMessage);
        return "Mail Sent Successfully";
    }

    // asynchronous emailing
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAsyncEmail(Email email) {
        try {
            applicationExecutor.execute(() -> {
                try {
                    sendEmail(email.getRecipient(), email.getSubject(), email.getMsgBody());
                    logger.info("Email sent to: " + email.getRecipient());
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to send email to: " + email.getRecipient(), e);
                    saveFailedEmail(email);
                }
            });
        } catch (RejectedExecutionException e) {
            logger.log(Level.WARNING, "Could not execute send email task to: " + email.getRecipient(), e);
            saveFailedEmail(email);
        }
    }

    private void saveFailedEmail(Email email) {
        failedEmailsLock.lock();
        try {
            failedEmailsList.add(email);
        } finally {
            failedEmailsLock.unlock();
        }
    }

    @Scheduled(initialDelay = 3 * 60 * 60 * 1000L, fixedDelay = 3 * 60 * 60 * 1000L)
    public void retryFailedEmails() {
        List<Email> copyList;
        failedEmailsLock.lock();
        try {
            copyList = new ArrayList<>(failedEmailsList);
            failedEmailsList.clear();
        } finally {
            failedEmailsLock.unlock();
        }

        for (Email email: copyList)
            sendAsyncEmail(email);
    }


    // helper methods

    public static boolean validateEmailFormat(String email) {
        return EmailValidator.getInstance().isValid(email);
    }
}

