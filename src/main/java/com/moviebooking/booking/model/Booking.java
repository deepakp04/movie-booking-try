package com.moviebooking.booking.model;

import com.moviebooking.auth.entity.User;
import com.moviebooking.catalog.model.Show;
import com.moviebooking.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    // Unique reference for this booking attempt, surfaced to the user and
    // reused as the payment order reference in the next module.
    @Column(nullable = false, unique = true, length = 40)
    private String transactionId;

    private LocalDateTime holdExpiresAt;
}
