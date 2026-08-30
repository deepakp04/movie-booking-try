package com.moviebooking.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl
        implements EmailService {

    private final JavaMailSender mailSender;

    public EmailServiceImpl(
            JavaMailSender mailSender) {

        this.mailSender = mailSender;
    }

    @Override
    public void sendOtpEmail(
            String toEmail,
            String otp) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setTo(toEmail);

        message.setSubject(
                "Movie Booking System - OTP Verification"
        );

        message.setText(
                "Your OTP is: "
                        + otp
                        + "\n\nThis OTP expires in 10 minutes."
        );

        mailSender.send(message);
    }
}