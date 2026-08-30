package com.moviebooking.catalog.dto;

import com.moviebooking.catalog.model.CbfcRating;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class MovieDetailResponse {
    private Long id;
    private String title;
    private String description;
    private String castMembers;
    private String director;
    private Integer durationMinutes;
    private CbfcRating cbfcRating;
    private String posterUrl;
    private String bannerUrl;
    private LocalDate releaseDate;
    private List<String> availableLanguages;
    private List<String> availableFormats;
    private Boolean hasCaptions;
}