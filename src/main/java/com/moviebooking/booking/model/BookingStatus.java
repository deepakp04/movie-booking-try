package com.moviebooking.booking.model;

public enum BookingStatus {
    PENDING_PAYMENT, // seats held, awaiting payment confirmation (next module)
    CONFIRMED,       // payment succeeded, seats permanently booked
    CANCELLED,       // user released the hold before paying
    EXPIRED          // 10-minute hold window lapsed without payment
}
