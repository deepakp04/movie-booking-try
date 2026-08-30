package com.moviebooking.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class TheatreShowResponse {
    private Long theatreId;
    private String theatreName;
    private String address;
    private List<ShowTimeResponse> shows;
}