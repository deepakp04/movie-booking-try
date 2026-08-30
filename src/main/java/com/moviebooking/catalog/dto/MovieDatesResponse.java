package com.moviebooking.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * confirmedUntil is the last date in the window that actually has shows, so the
 * UI can say "shows confirmed until 23 July" rather than silently offering empty
 * dates.
 */
@Getter
@AllArgsConstructor
public class MovieDatesResponse {
    private List<ShowDateResponse> dates;
    private LocalDate confirmedUntil;
}
