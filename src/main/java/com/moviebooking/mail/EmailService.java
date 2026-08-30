package com.moviebooking.mail;

public interface EmailService {

    void sendOtpEmail(
            String toEmail,
            String otp
    );
}