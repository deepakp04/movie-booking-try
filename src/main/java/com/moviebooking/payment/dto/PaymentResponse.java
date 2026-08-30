package com.moviebooking.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment confirmation response.
 */
public record PaymentResponse(
    boolean success,
    String message,
    String transactionId,
    String razorpayPaymentId,
    Long bookingId,
    BigDecimal amount,
    LocalDateTime paidAt
) {}
