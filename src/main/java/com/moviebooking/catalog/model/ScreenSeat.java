package com.moviebooking.catalog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moviebooking.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * One physical position in a screen's seat grid.
 *
 * This replaces the Screen.layoutJson matrix as the source of truth. Storing
 * seats as rows rather than a JSON blob is what makes per-seat CRUD and
 * per-seat tier assignment expressible at all.
 *
 * rowLabel + colIndex is the grid coordinate (unique per screen).
 * seatNumber is the human-facing number within the row, counting only SEAT
 * cells, so seatCode = rowLabel + seatNumber (e.g. "C7"). PATHWAY cells have a
 * null seatNumber.
 */
@Entity
@Table(
    name = "screen_seats",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_screen_seat_position",
        columnNames = {"screen_id", "row_label", "col_index"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreenSeat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false)
    @JsonIgnoreProperties({"theatre", "seatTiers"})
    private Screen screen;

    /** Null for PATHWAY cells, which are not sellable and so have no tier. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_tier_id")
    @JsonIgnoreProperties({"screen"})
    private SeatTier seatTier;

    @Column(name = "row_label", nullable = false, length = 4)
    private String rowLabel;

    @Column(name = "col_index", nullable = false)
    private Integer colIndex;

    @Column(name = "seat_number")
    private Integer seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 20)
    @Builder.Default
    private SeatType seatType = SeatType.SEAT;

    /** "C7" for a seat, null for a pathway. */
    @Transient
    public String getSeatCode() {
        return (seatNumber == null) ? null : rowLabel + seatNumber;
    }
}
