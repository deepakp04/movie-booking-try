package com.moviebooking.booking.model;

import com.moviebooking.auth.entity.User;
import com.moviebooking.catalog.model.Show;
import com.moviebooking.common.BaseEntity;
import com.moviebooking.voucher.model.Voucher;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Comma-separated seat codes, e.g. "A1,A2,A3" - kept simple since payment
    // module will own the richer transaction/log schema next.
    @Column(nullable = false, length = 200)
    private String seatCodes;

    @Column(nullable = false)
    private Integer numberOfSeats;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    /** Null on rows created before vouchers shipped — treat as PAYMENT_GATEWAY. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BookingPaymentMode paymentMode;

    /** Set when this booking was paid with a ticket voucher instead of the gateway. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @Column(length = 40)
    private String voucherCode;

    // Unique reference for this booking attempt, surfaced to the user and
    // reused as the payment order reference in the next module.
    @Column(nullable = false, unique = true, length = 40)
    private String transactionId;

    private LocalDateTime holdExpiresAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingAttendee> attendees = new ArrayList<>();

    /** Never null for the rest of the codebase. */
    public BookingPaymentMode effectivePaymentMode() {
        return paymentMode == null ? BookingPaymentMode.PAYMENT_GATEWAY : paymentMode;
    }

    public boolean isVoucherBooking() {
        return effectivePaymentMode() == BookingPaymentMode.VOUCHER || voucher != null;
    }
}
