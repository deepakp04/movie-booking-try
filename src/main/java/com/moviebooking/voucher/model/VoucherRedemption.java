package com.moviebooking.voucher.model;

import com.moviebooking.auth.entity.User;
import com.moviebooking.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row per booking paid with a voucher. This is the audit trail the admin
 * Vouchers tab reads: how many free tickets each booking consumed, on which
 * show, and how much ticket value the voucher covered.
 *
 * A cancellation reverses the redemption instead of deleting it, so the ledger
 * still records that the tickets were used and then restored.
 */
@Entity
@Table(name = "voucher_redemptions")
@Getter
@Setter
@NoArgsConstructor
public class VoucherRedemption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private Long bookingId;

    private Long showId;

    @Column(length = 200)
    private String movieTitle;

    @Column(length = 200)
    private String theatreName;

    @Column(nullable = false)
    private Integer ticketsUsed;

    /** Sum of the seat price snapshots this voucher covered. */
    @Column(precision = 10, scale = 2)
    private BigDecimal amountCovered;

    @Column(nullable = false)
    private LocalDateTime redeemedAt;

    /** Set when the booking was cancelled and the free tickets were returned. */
    private LocalDateTime reversedAt;
}
