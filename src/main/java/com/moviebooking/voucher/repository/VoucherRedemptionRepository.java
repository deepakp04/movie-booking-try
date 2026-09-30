package com.moviebooking.voucher.repository;

import com.moviebooking.voucher.model.VoucherRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, Long> {

    List<VoucherRedemption> findByVoucherIdOrderByRedeemedAtDesc(Long voucherId);

    List<VoucherRedemption> findByVoucherIdInAndReversedAtIsNull(Collection<Long> voucherIds);

    List<VoucherRedemption> findByBookingIdAndReversedAtIsNull(Long bookingId);

    /** How many distinct vouchers have been fully consumed (users who completed). */
    @Query("SELECT COUNT(DISTINCT r.voucher.id) FROM VoucherRedemption r WHERE r.reversedAt IS NULL")
    long countVouchersWithRedemptions();

    @Query("SELECT COALESCE(SUM(r.ticketsUsed), 0) FROM VoucherRedemption r WHERE r.reversedAt IS NULL")
    long sumTicketsUsed();

    @Query("SELECT COALESCE(SUM(r.amountCovered), 0) FROM VoucherRedemption r WHERE r.reversedAt IS NULL")
    java.math.BigDecimal sumAmountCovered();
}
