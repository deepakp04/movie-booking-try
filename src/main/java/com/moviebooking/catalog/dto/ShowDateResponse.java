package com.moviebooking.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * One entry in the date strip. hasShows drives the colour difference the spec
 * asks for between dates with confirmed screenings and dates without.
 */
@Getter
@AllArgsConstructor
public class ShowDateResponse {
    private LocalDate date;
    private boolean hasShows;
    private int showCount;
}
