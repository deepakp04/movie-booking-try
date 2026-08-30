package com.moviebooking.owner.dto;

import com.moviebooking.catalog.model.AudioLanguage;
import com.moviebooking.catalog.model.CbfcRating;
import com.moviebooking.catalog.model.MovieFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OwnerDTOs {

    // Screen Request - theatre is always inferred from the authenticated owner's token
    public record ScreenRequest(
        String name,
        Integer totalSeats
    ) {}

    public record ScreenResponse(
        Long id,
        String name,
        Integer totalSeats,
        String layoutJson
    ) {}

    public record ScreenUpdateRequest(String name, Integer totalSeats) {}

    // Seat designer payload: 1 = seat, 0 = pathway.
    public record SeatLayoutRequest(
        Integer rows,
        Integer cols,
        List<List<Integer>> matrix
    ) {}

    public record ScreenLayoutResponse(
        Long id,
        String name,
        Integer totalSeats,
        String layoutJson
    ) {}

    // Show Request - screenId must belong to the owner's own theatre (validated server-side)
    public record ShowRequest(
        Long screenId,
        Long movieId,
        LocalDateTime startTime,
        AudioLanguage language,
        MovieFormat format,
        Boolean hasCaptions,
        BigDecimal basePrice,
        List<TierPriceRequest> tierPrices,
        // Optional: seat codes to reserve at scheduling time (e.g. house seats,
        // complimentary blocks). These seats are marked BOOKED immediately.
        List<String> reservedSeatCodes
    ) {}

    public record ShowResponse(
        Long id,
        Long movieId,
        String movieTitle,
        Long screenId,
        String screenName,
        LocalDateTime startTime,
        AudioLanguage language,
        MovieFormat format,
        Boolean hasCaptions,
        BigDecimal basePrice
    ) {}

    public record MovieOption(
        Long id,
        String title,
        CbfcRating cbfcRating,
        Integer durationMinutes
    ) {}

    public record TheatreInfoResponse(
        Long id,
        String name,
        String address,
        Long cityId,
        String cityName,
        List<ScreenResponse> screens
    ) {}

    // ===== Phase 3: seat tiers, seat grid, per-show tier pricing =====

    public record SeatTierRequest(String name, Integer displayOrder, String colorHex) {}

    public record SeatTierResponse(
        Long id,
        Long screenId,
        String name,
        Integer displayOrder,
        String colorHex,
        Long seatCount
    ) {}

    // One cell of the designer grid. type is SEAT, PATHWAY or BLOCKED.
    // tierId is ignored for non-SEAT cells.
    public record SeatCellRequest(String type, Long tierId) {}

    public record LayoutSaveRequest(
        java.util.List<java.util.List<SeatCellRequest>> grid
    ) {}

    public record ScreenSeatResponse(
        Long id,
        String rowLabel,
        Integer colIndex,
        Integer seatNumber,
        String seatCode,
        String seatType,
        Long tierId,
        String tierName,
        String tierColorHex
    ) {}

    public record ScreenLayoutDetailResponse(
        Long screenId,
        String screenName,
        Integer totalSeats,
        Integer rows,
        Integer cols,
        boolean editable,
        String lockReason,
        java.util.List<SeatTierResponse> tiers,
        java.util.List<ScreenSeatResponse> seats
    ) {}

    public record SeatTierAssignRequest(Long tierId) {}

    // Price for one tier of one show. Pricing is per show by design.
    public record TierPriceRequest(Long tierId, java.math.BigDecimal price) {}

    public record TierPriceResponse(
        Long tierId,
        String tierName,
        String colorHex,
        java.math.BigDecimal price
    ) {}
}
