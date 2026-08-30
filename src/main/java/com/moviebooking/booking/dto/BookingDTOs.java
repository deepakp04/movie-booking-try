package com.moviebooking.booking.dto;

import com.moviebooking.booking.model.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class BookingDTOs {

    /**
     * One cell of the seat map.
     *
     * PATHWAY cells are included so the client can render the aisles exactly as
     * they were drawn in the Maintenance tab; they have a null status and no
     * price. Row and column are supplied so the client never has to parse a
     * seat code to work out geometry.
     */
    public record SeatInfo(
        String seatCode,
        String rowLabel,
        Integer colIndex,
        Integer seatNumber,
        String seatType,        // SEAT | PATHWAY | BLOCKED
        String status,          // AVAILABLE | HELD | BOOKED, null for non-seats
        Boolean heldByMe,
        String tierName,
        String tierColorHex,
        BigDecimal price
    ) {}

    public record TierLegend(
        String name,
        String colorHex,
        BigDecimal price
    ) {}

    public record SeatMapResponse(
        Long showId,
        String movieTitle,
        String theatreName,
        String screenName,
        LocalDateTime startTime,
        String language,
        String format,
        Boolean hasCaptions,
        Integer rows,
        Integer cols,
        Integer totalSeats,
        Integer availableSeats,
        Integer maxSeatsPerBooking,
        List<TierLegend> tiers,
        List<SeatInfo> seats
    ) {}

    public record HoldSeatsRequest(
        Long showId,
        List<String> seatCodes
    ) {}

    /** Per-seat breakdown so the checkout screen can itemize a tiered basket. */
    public record BookedSeatLine(
        String seatCode,
        String tierName,
        BigDecimal price
    ) {}

    public record BookingResponse(
        Long bookingId,
        String transactionId,
        Long showId,
        String movieTitle,
        String theatreName,
        String screenName,
        LocalDateTime showStartTime,
        List<String> seatCodes,
        List<BookedSeatLine> seatLines,
        Integer numberOfSeats,
        BigDecimal totalAmount,
        BookingStatus status,
        LocalDateTime holdExpiresAt
    ) {}
}
