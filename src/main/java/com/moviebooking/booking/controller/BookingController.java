package com.moviebooking.booking.controller;

import com.moviebooking.booking.dto.BookingDTOs.*;
import com.moviebooking.booking.service.BookingService;
import com.moviebooking.common.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/booking")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/shows/{showId}/seats")
    public ApiResponse<SeatMapResponse> getSeatMap(@PathVariable("showId") Long showId) {
        return new ApiResponse<>(true, "Seat map retrieved successfully", bookingService.getSeatMap(showId));
    }

    @PostMapping("/hold")
    public ApiResponse<BookingResponse> holdSeats(@RequestBody HoldSeatsRequest req) {
        return new ApiResponse<>(true, "Seats held for 10 minutes. Complete payment before the hold expires.",
                bookingService.holdSeats(req));
    }

    @GetMapping("/{bookingId}")
    public ApiResponse<BookingResponse> getBooking(@PathVariable("bookingId") Long bookingId) {
        return new ApiResponse<>(true, "Booking retrieved successfully", bookingService.getBooking(bookingId));
    }

    @PostMapping("/{bookingId}/cancel")
    public ApiResponse<Void> cancelBooking(@PathVariable("bookingId") Long bookingId) {
        bookingService.cancelBooking(bookingId);
        return new ApiResponse<>(true, "Booking cancelled and seats released.", null);
    }

    /**
     * Get all bookings for the current user - "My Bookings" endpoint.
     */
    @GetMapping("/my-bookings")
    public ApiResponse<List<BookingResponse>> getMyBookings() {
        return new ApiResponse<>(true, "Bookings retrieved successfully", bookingService.getAllBookingsForUser());
    }
}
