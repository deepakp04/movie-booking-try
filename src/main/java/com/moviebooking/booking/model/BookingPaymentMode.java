package com.moviebooking.booking.model;

/**
 * How a booking was settled. Voucher bookings skip the payment gateway
 * entirely: the seats are confirmed the moment the attendee details are
 * submitted and the free-ticket balance is debited.
 *
 * Rows that predate vouchers have a null column in the database; treat null as
 * PAYMENT_GATEWAY.
 */
public enum BookingPaymentMode {
    PAYMENT_GATEWAY,
    VOUCHER
}
