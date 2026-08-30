package com.moviebooking.catalog.dto;

import com.moviebooking.catalog.model.CbfcRating;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MovieSummaryResponse {
    private Long id;
    private String title;
    private String posterUrl;
    private Integer durationMinutes;
    private CbfcRating cbfcRating;
    private List<String> availableLanguages;
    private List<String> availableFormats;
}