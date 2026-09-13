package com.moviebooking.mail.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviebooking.mail.model.EmailOutbox;
import com.moviebooking.mail.repository.EmailOutboxRepository;
import com.moviebooking.mail.EmailService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Background processor that picks up pending emails from the outbox
 * and sends them via SMTP. Runs every 10 seconds.
 *
 * This keeps SMTP blocking off the WebFlux event-loop / request thread.
 * A slow email server will not block booking/payment confirmations.
 */
@Service
public class EmailOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxProcessor.class);

    private final EmailOutboxRepository outboxRepository;
    private final JavaMailSender mailSender;

    public EmailOutboxProcessor(EmailOutboxRepository outboxRepository,
                                 JavaMailSender mailSender) {
        this.outboxRepository = outboxRepository;
        this.mailSender = mailSender;
    }

    @Scheduled(fixedDelay = 10000) // Every 10 seconds
    @Transactional
    public void processPendingEmails() {
        List<EmailOutbox> pending;
        try {
            pending = outboxRepository.findPendingForProcessing();
        } catch (Exception e) {
            log.warn("Failed to query email outbox: {}", e.getMessage());
            return;
        }

        if (pending.isEmpty()) return;

        log.info("Processing {} pending email(s) from outbox", pending.size());

        for (EmailOutbox email : pending) {
            try {
                sendEmail(email);
                email.setStatus(EmailOutbox.EmailStatus.SENT);
                email.setSentAt(LocalDateTime.now());
                outboxRepository.save(email);
                log.info("Email sent: {} to {} (booking {})",
                        email.getEmailType(), email.getRecipientEmail(), email.getBookingId());
            } catch (Exception e) {
                email.setRetryCount(email.getRetryCount() + 1);
                email.setFailureReason(truncate(e.getMessage(), 900));

                if (email.getRetryCount() >= email.getMaxRetries()) {
                    email.setStatus(EmailOutbox.EmailStatus.FAILED);
                    log.error("Email permanently failed after {} retries: booking {} type {} to {} — {}",
                            email.getMaxRetries(), email.getBookingId(), email.getEmailType(),
                            email.getRecipientEmail(), e.getMessage());
                } else {
                    log.warn("Email send failed (attempt {}/{}): booking {} — {}",
                            email.getRetryCount(), email.getMaxRetries(),
                            email.getBookingId(), e.getMessage());
                }
                outboxRepository.save(email);
            }
        }
    }

    private void sendEmail(EmailOutbox email) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(email.getRecipientEmail());
        helper.setSubject(email.getSubject());
        helper.setText(email.getHtmlBody(), true); // true = HTML
        helper.setFrom("PVR Cinemas <cinemabooking45@gmail.com>");

        mailSender.send(message);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
