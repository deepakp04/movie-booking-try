package com.moviebooking.catalog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moviebooking.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * A pricing class within one screen, e.g. BUDGET / PREMIUM / DIAMOND.
 *
 * Tiers belong to a screen rather than a theatre because two screens in the
 * same multiplex routinely have different seating classes.
 *
 * Deliberately carries no price: pricing is per show (see ShowTierPrice), so
 * the same DIAMOND row can cost different amounts for a matinee and a weekend
 * IMAX show.
 */
@Entity
@Table(
    name = "seat_tiers",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_seat_tier_screen_name",
        columnNames = {"screen_id", "name"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatTier extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false)
    @JsonIgnoreProperties({"theatre", "seatTiers"})
    private Screen screen;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    /** Hex colour used by the seat map legend, e.g. "#C9A227". */
    @Column(name = "color_hex", length = 7)
    @Builder.Default
    private String colorHex = "#7A7A7A";
}
