package com.moviebooking.booking.model;

import com.moviebooking.catalog.model.ScreenSeat;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One row per physical seat per show. Generated lazily the first time a show's
// seat map is requested (see BookingService#ensureSeatsInitialized).
@Entity
@Table(name = "show_seats", uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "seat_code"}))
@Getter
@Setter
@NoArgsConstructor
public class ShowSeat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(name = "seat_code", nullable = false, length = 10)
    private String seatCode; // e.g. "A1", "B10"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status = SeatStatus.AVAILABLE;

    // Who currently holds this seat (during the 10-minute payment window)
    private Long heldByUserId;

    private LocalDateTime holdExpiresAt;

    // Which booking this seat is attached to (HELD or BOOKED); null when AVAILABLE
    private Long bookingId;

    // ---- Phase 4: link to the physical seat and snapshot its price ----

    /**
     * The screen_seats row this show-seat was generated from. Null for shows
     * materialized before Phase 4, or for screens whose layout has not been
     * drawn in the Maintenance tab yet.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_seat_id")
    private ScreenSeat screenSeat;

    /**
     * Tier name copied at materialization time rather than joined at read time.
     * Denormalized on purpose: renaming or deleting a tier later must not
     * rewrite what a customer already saw and paid for.
     */
    @Column(name = "tier_name", length = 50)
    private String tierName;

    /**
     * The price for this seat in this show, resolved once when the seat map is
     * first materialized. This is the money column: a later price edit cannot
     * retroactively change it, which is what keeps revenue reporting truthful.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;
}
