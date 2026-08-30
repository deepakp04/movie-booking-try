package com.moviebooking.catalog.controller;

import com.moviebooking.catalog.dto.*;
import com.moviebooking.catalog.service.CatalogService;
import com.moviebooking.common.response.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/cities")
    public ApiResponse<List<CityResponse>> getCities() {
        return new ApiResponse<>(true, "Cities retrieved successfully", catalogService.getAllCities());
    }
    
    

    @GetMapping("/movies")
    public ApiResponse<List<MovieSummaryResponse>> getMoviesByCity(@RequestParam("cityId") Long cityId) {
        return new ApiResponse<>(true, "Movies retrieved successfully", catalogService.getMoviesRunningInCity(cityId));
    }

    @GetMapping("/movies/{movieId}")
    public ApiResponse<MovieDetailResponse> getMovieDetails(
            @PathVariable("movieId") Long movieId,
            @RequestParam("cityId") Long cityId) {
        return new ApiResponse<>(true, "Movie details retrieved successfully", catalogService.getMovieDetails(movieId, cityId));
    }

    @GetMapping("/movies/{movieId}/shows")
    public ApiResponse<List<TheatreShowResponse>> getShows(
            @PathVariable("movieId") Long movieId,
            @RequestParam("cityId") Long cityId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return new ApiResponse<>(true, "Showtimes retrieved successfully", catalogService.getShowsForMovieAndDate(movieId, cityId, date));
    }

    @GetMapping("/movies/{movieId}/dates")
    public ApiResponse<MovieDatesResponse> getShowDates(
            @PathVariable("movieId") Long movieId,
            @RequestParam("cityId") Long cityId,
            @RequestParam(value = "days", required = false, defaultValue = "7") int days) {
        return new ApiResponse<>(true, "Dates retrieved",
                catalogService.getAvailableDates(movieId, cityId, days));
    }
}
