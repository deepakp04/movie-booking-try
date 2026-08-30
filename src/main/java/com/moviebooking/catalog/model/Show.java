package com.moviebooking.catalog.model;

import com.moviebooking.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Show extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AudioLanguage language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovieFormat format;

    @Column(nullable = false)
    private Boolean hasCaptions = false;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;
}