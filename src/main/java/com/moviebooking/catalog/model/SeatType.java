package com.moviebooking.catalog.model;

/**
 * What a single cell in a screen's seat grid represents.
 *
 * PATHWAY cells occupy a position in the grid but are not sellable and do not
 * consume a seat number, so row A with a pathway in the middle still numbers
 * its seats 1..n continuously - which is how real cinema layouts read.
 */
public enum SeatType {
    SEAT,
    PATHWAY,
    BLOCKED
}
