package com.moviebooking.admin.dto;

import com.moviebooking.catalog.model.AudioLanguage;
import com.moviebooking.catalog.model.CbfcRating;
import com.moviebooking.catalog.model.MovieFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AdminDTOs {

    // City Request
    public record CityRequest(String name, String state) {}

    // Movie Request
    public record MovieRequest(
        String title,
        String description,
        String castMembers,
        String director,
        Integer durationMinutes,
        CbfcRating cbfcRating,
        String posterUrl,
        String bannerUrl,
        LocalDate releaseDate,
        java.util.Set<AudioLanguage> availableLanguages,
        java.util.Set<MovieFormat> availableFormats
    ) {}

    public record MovieUpdateRequest(
        String title,
        String description,
        String castMembers,
        String director,
        Integer durationMinutes,
        CbfcRating cbfcRating,
        String posterUrl,
        String bannerUrl,
        LocalDate releaseDate,
        java.util.Set<AudioLanguage> availableLanguages,
        java.util.Set<MovieFormat> availableFormats
    ) {}

    // Flat view of a Movie. availableLanguages/availableFormats are LAZY
    // @ElementCollections, so they must be resolved inside the service
    // transaction now that open-in-view is disabled.
    public record MovieResponse(
        Long id,
        String title,
        String description,
        String castMembers,
        String director,
        Integer durationMinutes,
        String cbfcRating,
        String posterUrl,
        String bannerUrl,
        LocalDate releaseDate,
        List<String> availableLanguages,
        List<String> availableFormats
    ) {}

    public record CityUpdateRequest(String name, String state) {}

    public record CityResponse(Long id, String name, String state) {}

    public record TheatreUpdateRequest(String name, String address, Long cityId) {}

    public record ScreenUpdateRequest(String name, Integer totalSeats) {}

    // Theatre Request
    public record TheatreRequest(
        Long cityId,
        String name,
        String address
    ) {}

    // Assign a new THEATRE_OWNER account to a theatre in one step.
    // The owner is provisioned directly by admin (pre-verified, active) so
    // they can log in immediately with the given credentials.
    public record AssignOwnerRequest(
        String name,
        String email,
        String password
    ) {}

    // Screen Request
    public record ScreenRequest(
        String name,
        Integer totalSeats
    ) {}

    // Seat designer payload. matrix is a rows x cols grid: 1 = seat, 0 = pathway.
    public record SeatLayoutRequest(
        Integer rows,
        Integer cols,
        java.util.List<java.util.List<Integer>> matrix
    ) {}

    // Show Request
    public record ShowRequest(
        Long screenId,
        Long movieId,
        LocalDateTime startTime,
        AudioLanguage language,
        MovieFormat format,
        Boolean hasCaptions,
        BigDecimal basePrice,
        // One price per seat tier on the chosen screen. basePrice is retained as
        // the fallback for shows created before tier pricing existed.
        List<TierPriceRequest> tierPrices,
        // Optional: seat codes to reserve at scheduling time (e.g. house seats,
        // complimentary blocks). These seats are marked BOOKED immediately.
        List<String> reservedSeatCodes
    ) {}
    
    public record ScreenResponse(
            Long id,
            String name,
            Integer totalSeats,
            String layoutJson
        ) {}

    public record ScreenLayoutResponse(
            Long id,
            String name,
            Integer totalSeats,
            String layoutJson
        ) {}

    // Flat, cycle-free view of a Show. Built inside the service transaction so
    // the lazy movie/screen/theatre associations are initialized before Jackson
    // serializes — this is what prevents the 500 on GET /api/admin/shows.
    // Field names intentionally match what admin.js expects (movieTitle,
    // theatreName, screenName, price, format, language, hasCaptions).
    public record ShowResponse(
            Long id,
            Long movieId,
            String movieTitle,
            Long screenId,
            String screenName,
            String theatreName,
            java.time.LocalDateTime startTime,
            String language,
            String format,
            Boolean hasCaptions,
            java.math.BigDecimal price
        ) {}

        public record TheatreResponse(
            Long id,
            String name,
            String address,
            Long cityId,
            String cityName,
            List<ScreenResponse> screens,
            Long ownerId,
            String ownerName,
            String ownerEmail
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
