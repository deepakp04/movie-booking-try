package com.moviebooking.stream.dto;

import java.math.BigDecimal;

/**
 * DTO for Server-Sent Events (SSE) seat updates.
 * Pushed to clients when a seat's status changes (HELD, BOOKED, AVAILABLE).
 */
public record SeatUpdateEvent(
    Long showId,
    String seatCode,
    String status,          // AVAILABLE, HELD, BOOKED
    Long heldByUserId,      // null unless HELD
    Boolean heldByMe,       // true if held by current user
    String reason,          // "HELD", "BOOKED", "RELEASED", "EXPIRED"
    BigDecimal price        // Price for display when seat becomes available
) {}
