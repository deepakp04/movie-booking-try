package com.moviebooking.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response containing Razorpay order details for client-side checkout.
 */
public record OrderResponse(
    String razorpayOrderId,      // Razorpay order ID
    String transactionId,        // Our internal transaction UUID
    Long bookingId,
    BigDecimal amount,
    String currency,
    String razorpayKeyId,        // Razorpay public key (test mode)
    LocalDateTime createdAt,
    LocalDateTime expiresAt
) {}
