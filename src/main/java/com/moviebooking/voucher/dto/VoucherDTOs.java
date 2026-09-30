package com.moviebooking.voucher.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class VoucherDTOs {

    /** One voucher-funded booking in a user's voucher history. */
    public record RedemptionRow(
        Long redemptionId,
        Long bookingId,
        String movieTitle,
        String theatreName,
        Integer ticketsUsed,
        BigDecimal amountCovered,
        LocalDateTime redeemedAt,
        boolean reversed
    ) {}

    /**
     * Everything the customer surfaces need: the landing-page marquee, the info
     * box, and the voucher box on the seat-selection page.
     */
    public record MyVoucherResponse(
        Long voucherId,
        String code,
        Integer totalFreeTickets,
        Integer remainingFreeTickets,
        Integer ticketsRedeemed,
        BigDecimal valueRedeemed,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        String status,
        Boolean active,
        Boolean eligibleForNewVoucher,
        BigDecimal lifetimeSpend,
        BigDecimal eligibilityThreshold,
        List<RedemptionRow> redemptions
    ) {}

    public record VoucherValidationRequest(String code, Integer seats) {}

    public record VoucherValidationResponse(
        String code,
        Integer remainingFreeTickets,
        Integer requestedSeats,
        Integer ticketsCovered,
        Integer remainingAfter,
        Boolean coversAll,
        LocalDateTime expiresAt,
        String message
    ) {}

    /** A customer whose paid spend crossed the threshold and has no active voucher. */
    public record EligibleUserRow(
        Long userId,
        String name,
        String email,
        String phone,
        BigDecimal lifetimeSpend,
        Long confirmedBookings,
        LocalDateTime lastBookingAt,
        Long previousVouchers,
        String previousVoucherStatus
    ) {}

    /** One row of the admin voucher table. */
    public record VoucherRow(
        Long voucherId,
        Long userId,
        String userName,
        String userEmail,
        String phone,
        String code,
        Integer totalFreeTickets,
        Integer remainingFreeTickets,
        Integer ticketsUsed,
        BigDecimal valueRedeemed,
        Integer redemptionCount,
        String status,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        LocalDateTime lastUsedAt,
        LocalDateTime completedAt,
        String issuedByName
    ) {}

    public record VoucherStats(
        long eligibleUsers,
        long activeVouchers,
        long exhaustedVouchers,
        long expiredVouchers,
        long freeTicketsRemaining,
        long freeTicketsRedeemed,
        long completedUsers,
        BigDecimal valueRedeemed
    ) {}

    public record VoucherListResponse(
        VoucherStats stats,
        List<VoucherRow> vouchers,
        int filteredCount
    ) {}

    public record IssueVoucherRequest(List<Long> userIds) {}

    public record IssueVoucherResponse(
        int issued,
        int skipped,
        List<String> messages,
        List<VoucherRow> vouchers
    ) {}
}
