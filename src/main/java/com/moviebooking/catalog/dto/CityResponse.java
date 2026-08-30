package com.moviebooking.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CityResponse {
    private Long id;
    private String name;
    private String state;
}