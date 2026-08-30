package com.moviebooking.payment.dto;

import java.math.BigDecimal;

/**
 * Request to create a Razorpay order for a booking.
 */
public record CreateOrderRequest(
    Long bookingId,
    String currency
) {}
