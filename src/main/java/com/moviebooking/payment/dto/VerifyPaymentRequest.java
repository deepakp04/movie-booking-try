package com.moviebooking.payment.dto;

import java.time.LocalDateTime;

/**
 * Payment verification request from client after Razorpay checkout.
 */
public record VerifyPaymentRequest(
    String razorpayOrderId,
    String razorpayPaymentId,
    String razorpaySignature
) {}
