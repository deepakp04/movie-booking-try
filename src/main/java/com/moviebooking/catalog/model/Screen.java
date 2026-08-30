package com.moviebooking.catalog.model;

import com.moviebooking.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "screens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Screen extends BaseEntity {
    @Column(nullable = false, length = 50)
    private String name; // e.g., "Screen 1", "IMAX Screen"

    @Column
    @Builder.Default
    private Integer totalSeats = 0;

    // Optional custom seat layout saved by the seat designer, stored as JSON:
    // {"rows":6,"cols":10,"matrix":[[1,1,0,...],...]} where 1 = seat, 0 = pathway.
    // Nullable by design: existing screens with no custom layout keep the old
    // auto-generated grid behaviour, so this is a non-breaking addition.
    @Column(columnDefinition = "TEXT")
    private String layoutJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theatre_id", nullable = false)
    private Theatre theatre;
}