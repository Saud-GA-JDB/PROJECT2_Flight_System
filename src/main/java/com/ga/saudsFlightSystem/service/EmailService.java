package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.model.Email;
import java.io.File;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;


@Service
public class EmailService {
    @Autowired
    private ExecutorService applicationExecutor;
    private static final Logger logger = Logger.getLogger(EmailService.class.getName());

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
                }
            });
        } catch (RejectedExecutionException e) {
            logger.log(Level.WARNING, "Could not execute send email task to: " + email.getRecipient(), e);
        }
    }


    // helper methods

    public static boolean validateEmailFormat(String email) {
        return EmailValidator.getInstance().isValid(email);
    }
}

