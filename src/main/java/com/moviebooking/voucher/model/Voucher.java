package com.moviebooking.voucher.model;

import com.moviebooking.auth.entity.User;
import com.moviebooking.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A loyalty voucher worth {@code totalFreeTickets} free tickets, issued by an
 * admin to a customer whose lifetime paid spend crossed the eligibility
 * threshold. Each voucher carries a unique code that is bound to the account it
 * was issued to — it cannot be transferred or used by anybody else.
 */
@Entity
@Table(
        name = "vouchers",
        uniqueConstraints = @UniqueConstraint(columnNames = "code")
)
@Getter
@Setter
@NoArgsConstructor
public class Voucher extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Unique, account-bound redemption code, e.g. {@code PVR4-7K2M9Q}. */
    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false)
    private Integer totalFreeTickets = 4;

    @Column(nullable = false)
    private Integer remainingFreeTickets = 4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherStatus status = VoucherStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    /**
     * End of the validity window, exclusive: the voucher is usable right up to
     * this instant. Issued on day 1, it is valid through day 30 23:59 and turns
     * invalid the moment day 31 begins.
     */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** Admin who approved/sent the voucher. */
    private Long issuedByUserId;

    /** Last time free tickets were consumed (or restored) on this voucher. */
    private LocalDateTime lastRedeemedAt;

    /** True while the voucher still has free tickets inside its validity window. */
    public boolean isUsable(LocalDateTime now) {
        return status == VoucherStatus.ACTIVE
                && remainingFreeTickets != null
                && remainingFreeTickets > 0
                && expiresAt != null
                && now.isBefore(expiresAt);
    }
}
