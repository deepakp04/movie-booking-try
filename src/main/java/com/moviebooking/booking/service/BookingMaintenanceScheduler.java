package com.moviebooking.booking.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingMaintenanceScheduler {

    private final BookingService bookingService;

    public BookingMaintenanceScheduler(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // Backstop for the opportunistic expiry check that already runs on every
    // seat-map/hold request - this guarantees seats free up even if nobody
    // happens to hit this show again right after a hold lapses.
    @Scheduled(fixedRate = 60000)
    public void releaseExpiredHolds() {
        bookingService.expireStaleHolds();
    }
}
