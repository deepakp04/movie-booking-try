package com.moviebooking.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ShowTimeResponse {
    private Long showId;
    private String screenName;
    private LocalDateTime startTime;
    private String language;
    private String format;
    private Boolean hasCaptions;
    private BigDecimal price;

    // Availability drives the colour of each showtime chip and the sold-out
    // hover state. availableSeats falls back to the screen's capacity for shows
    // whose seat map has not been materialized yet.
    private Integer totalSeats;
    private Integer availableSeats;
    private Boolean soldOut;
}
