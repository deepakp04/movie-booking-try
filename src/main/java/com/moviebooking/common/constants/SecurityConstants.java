package com.moviebooking.common.constants;

public class SecurityConstants {

    private SecurityConstants() {}

    public static final int MAX_OTP_ATTEMPTS = 5;

    public static final int OTP_EXPIRY_MINUTES = 10;

    public static final int OTP_RESEND_COOLDOWN_SECONDS = 60;

    public static final int MAX_OTP_RESENDS = 5;
}