package com.moviebooking.catalog.model;

public enum MovieFormat {
    TWO_D("2D"),
    THREE_D("3D"),
    IMAX_2D("IMAX 2D"),
    IMAX_3D("IMAX 3D"),
    FOUR_DX("4DX");

    private final String value;

    MovieFormat(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}