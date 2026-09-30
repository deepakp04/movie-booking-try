package com.moviebooking.voucher.model;

/**
 * Lifecycle of a ticket voucher.
 *
 * ACTIVE    — issued, unused tickets remain, and the validity window is open.
 * EXHAUSTED — all free tickets have been redeemed (the user "completed" it).
 * EXPIRED   — the 30-day validity window closed with tickets still unused.
 */
public enum VoucherStatus {
    ACTIVE,
    EXHAUSTED,
    EXPIRED
}
