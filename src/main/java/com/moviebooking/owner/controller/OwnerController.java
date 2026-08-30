package com.moviebooking.owner.controller;

import com.moviebooking.common.response.ApiResponse;
import com.moviebooking.booking.dto.BookingDTOs.BookingResponse;
import com.moviebooking.owner.dto.OwnerDTOs.*;
import com.moviebooking.owner.service.OwnerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/owner")
public class OwnerController {

    private final OwnerService ownerService;

    public OwnerController(OwnerService ownerService) {
        this.ownerService = ownerService;
    }

    @GetMapping("/theatre")
    public ApiResponse<TheatreInfoResponse> getMyTheatre() {
        return new ApiResponse<>(true, "Theatre details retrieved successfully", ownerService.getMyTheatre());
    }

    @PostMapping("/screens")
    public ApiResponse<ScreenResponse> createScreen(@RequestBody ScreenRequest req) {
        return new ApiResponse<>(true, "Screen created successfully", ownerService.createScreen(req));
    }

    @GetMapping("/screens")
    public ApiResponse<List<ScreenResponse>> getMyScreens() {
        return new ApiResponse<>(true, "Screens retrieved successfully", ownerService.getMyScreens());
    }

    @GetMapping("/movies")
    public ApiResponse<List<MovieOption>> getAvailableMovies() {
        return new ApiResponse<>(true, "Movies retrieved successfully", ownerService.getAvailableMovies());
    }

    @PostMapping("/shows")
    public ApiResponse<ShowResponse> scheduleShow(@RequestBody ShowRequest req) {
        return new ApiResponse<>(true, "Show scheduled successfully", ownerService.scheduleShow(req));
    }

    @GetMapping("/shows")
    public ApiResponse<List<ShowResponse>> getMyShows(
            @RequestParam(value = "scope", required = false, defaultValue = "upcoming") String scope) {
        return new ApiResponse<>(true, "Shows retrieved successfully", ownerService.getMyShows(scope));
    }

    @DeleteMapping("/shows/{id}")
    public ApiResponse<Void> cancelShow(@PathVariable("id") Long id) {
        ownerService.cancelShow(id);
        return new ApiResponse<>(true, "Show cancelled successfully", null);
    }

    // ===== Parity with the admin portal (Phase 2b) =====

    @PutMapping("/screens/{screenId}")
    public ApiResponse<ScreenResponse> updateScreen(@PathVariable("screenId") Long screenId,
                                                    @RequestBody ScreenUpdateRequest req) {
        return new ApiResponse<>(true, "Screen updated successfully",
                ownerService.updateScreen(screenId, req));
    }

    @DeleteMapping("/screens/{screenId}")
    public ApiResponse<Void> deleteScreen(@PathVariable("screenId") Long screenId) {
        ownerService.deleteScreen(screenId);
        return new ApiResponse<>(true, "Screen deleted successfully", null);
    }

    @PutMapping("/screens/{screenId}/layout")
    public ApiResponse<ScreenLayoutResponse> updateScreenLayout(@PathVariable("screenId") Long screenId,
                                                                @RequestBody SeatLayoutRequest req) {
        return new ApiResponse<>(true, "Seat layout saved",
                ownerService.updateScreenLayout(screenId, req));
    }

    @GetMapping("/screens/{screenId}/layout")
    public ApiResponse<ScreenLayoutResponse> getScreenLayout(@PathVariable("screenId") Long screenId) {
        return new ApiResponse<>(true, "Layout retrieved", ownerService.getScreenLayout(screenId));
    }

    // ===== Seat tiers & layout designer, owner-scoped (Phase 3) =====

    @GetMapping("/screens/{screenId}/tiers")
    public ApiResponse<List<SeatTierResponse>> listTiers(@PathVariable("screenId") Long screenId) {
        return new ApiResponse<>(true, "Tiers retrieved", ownerService.listTiers(screenId));
    }

    @PostMapping("/screens/{screenId}/tiers")
    public ApiResponse<SeatTierResponse> createTier(@PathVariable("screenId") Long screenId,
                                                    @RequestBody SeatTierRequest req) {
        return new ApiResponse<>(true, "Seat tier created", ownerService.createTier(screenId, req));
    }

    @PutMapping("/tiers/{tierId}")
    public ApiResponse<SeatTierResponse> updateTier(@PathVariable("tierId") Long tierId,
                                                    @RequestBody SeatTierRequest req) {
        return new ApiResponse<>(true, "Seat tier updated", ownerService.updateTier(tierId, req));
    }

    @DeleteMapping("/tiers/{tierId}")
    public ApiResponse<Void> deleteTier(@PathVariable("tierId") Long tierId) {
        ownerService.deleteTier(tierId);
        return new ApiResponse<>(true, "Seat tier deleted", null);
    }

    @GetMapping("/screens/{screenId}/seats")
    public ApiResponse<ScreenLayoutDetailResponse> getLayoutDetail(@PathVariable("screenId") Long screenId) {
        return new ApiResponse<>(true, "Layout retrieved", ownerService.getLayoutDetail(screenId));
    }

    @PutMapping("/screens/{screenId}/seats")
    public ApiResponse<ScreenLayoutDetailResponse> saveLayout(@PathVariable("screenId") Long screenId,
                                                             @RequestBody LayoutSaveRequest req) {
        return new ApiResponse<>(true, "Seat layout saved", ownerService.saveLayout(screenId, req));
    }

    @PutMapping("/seats/{seatId}/tier")
    public ApiResponse<ScreenSeatResponse> assignSeatTier(@PathVariable("seatId") Long seatId,
                                                         @RequestBody SeatTierAssignRequest req) {
        return new ApiResponse<>(true, "Seat tier updated", ownerService.assignSeatTier(seatId, req));
    }

    @GetMapping("/shows/{showId}/prices")
    public ApiResponse<List<TierPriceResponse>> getShowPrices(@PathVariable("showId") Long showId) {
        return new ApiResponse<>(true, "Prices retrieved", ownerService.getShowPrices(showId));
    }

    // ===== Booking logs (Owner can view bookings for their theatre) =====
    @GetMapping("/bookings")
    public ApiResponse<List<BookingResponse>> getMyTheatreBookings() {
        return new ApiResponse<>(true, "Theatre bookings retrieved successfully", ownerService.getMyTheatreBookings());
    }

    @GetMapping("/shows/{showId}/bookings")
    public ApiResponse<List<BookingResponse>> getMyShowBookings(@PathVariable("showId") Long showId) {
        return new ApiResponse<>(true, "Show bookings retrieved successfully", ownerService.getShowBookings(showId));
    }
}
