package com.moviebooking.catalog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moviebooking.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * The price of one seat tier for one show.
 *
 * Pricing lives here rather than on SeatTier because price is a property of the
 * screening, not of the furniture: a DIAMOND seat costs more for a Saturday
 * night IMAX show than for a Tuesday matinee.
 *
 * Resolution order when pricing a seat is: ShowTierPrice, then Show.basePrice
 * as a fallback. The fallback is what keeps shows created before this table
 * existed sellable.
 */
@Entity
@Table(
    name = "show_tier_prices",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_show_tier_price",
        columnNames = {"show_id", "seat_tier_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowTierPrice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    @JsonIgnoreProperties({"screen", "movie"})
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_tier_id", nullable = false)
    @JsonIgnoreProperties({"screen"})
    private SeatTier seatTier;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
}
