package com.moviebooking.admin.controller;

import com.moviebooking.admin.dto.AdminDTOs.*;
import com.moviebooking.admin.service.AdminService;
import com.moviebooking.booking.dto.BookingDTOs.BookingResponse;
import com.moviebooking.catalog.model.*;
import com.moviebooking.common.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // --- CITY ENDPOINTS ---
    @PostMapping("/cities")
    public ApiResponse<CityResponse> createCity(@RequestBody CityRequest req) {
        return new ApiResponse<>(true, "City created successfully", adminService.createCity(req));
    }

    @GetMapping("/cities")
    public ApiResponse<List<CityResponse>> getCities() {
        return new ApiResponse<>(true, "Cities retrieved successfully", adminService.getAllCities());
    }

    @DeleteMapping("/cities/{id}")
    public ApiResponse<Void> deleteCity(@PathVariable("id") Long id) {
        adminService.deleteCity(id);
        return new ApiResponse<>(true, "City deleted successfully", null);
    }

    // --- MOVIE ENDPOINTS ---
    @PostMapping("/movies")
    public ApiResponse<MovieResponse> createMovie(@RequestBody MovieRequest req) {
        return new ApiResponse<>(true, "Movie added to library", adminService.createMovie(req));
    }

    @GetMapping("/movies")
    public ApiResponse<List<MovieResponse>> getMovies() {
        return new ApiResponse<>(true, "Movies retrieved successfully", adminService.getAllMovies());
    }

    @DeleteMapping("/movies/{id}")
    public ApiResponse<Void> deleteMovie(@PathVariable("id") Long id) {
        adminService.deleteMovie(id);
        return new ApiResponse<>(true, "Movie deleted successfully", null);
    }

    // --- THEATRE & SCREEN ENDPOINTS ---
    @PostMapping("/theatres")
    public ApiResponse<TheatreResponse> createTheatre(@RequestBody TheatreRequest req) {
        return new ApiResponse<>(true, "Theatre created successfully", adminService.createTheatre(req));
    }

    @GetMapping("/theatres")
    public ApiResponse<List<TheatreResponse>> getTheatres() {
        return new ApiResponse<>(true, "Theatres retrieved successfully", adminService.getAllTheatres());
    }

    @GetMapping("/cities/{cityId}/theatres")
    public ApiResponse<List<TheatreResponse>> getTheatresByCity(@PathVariable("cityId") Long cityId) {
        return new ApiResponse<>(true, "Theatres retrieved successfully", adminService.getTheatresByCity(cityId));
    }

    @PostMapping("/theatres/{theatreId}/owner")
    public ApiResponse<TheatreResponse> assignOwner(@PathVariable("theatreId") Long theatreId, @RequestBody AssignOwnerRequest req) {
        return new ApiResponse<>(true, "Theatre owner assigned successfully", adminService.assignOwner(theatreId, req));
    }

    @DeleteMapping("/theatres/{theatreId}/owner")
    public ApiResponse<TheatreResponse> unassignOwner(@PathVariable("theatreId") Long theatreId) {
        return new ApiResponse<>(true, "Theatre owner unassigned successfully", adminService.unassignOwner(theatreId));
    }

    @PostMapping("/theatres/{theatreId}/screens")
    public ApiResponse<ScreenResponse> createScreen(@PathVariable("theatreId") Long theatreId, @RequestBody ScreenRequest req) {
        return new ApiResponse<>(true, "Screen created successfully", adminService.createScreen(theatreId, req));
    }

    @GetMapping("/theatres/{theatreId}/screens")
    public ApiResponse<List<ScreenResponse>> getScreens(@PathVariable("theatreId") Long theatreId) {
        return new ApiResponse<>(true, "Screens retrieved successfully", adminService.getScreensByTheatre(theatreId));
    }

    @PutMapping("/screens/{screenId}/layout")
    public ApiResponse<ScreenLayoutResponse> updateScreenLayout(@PathVariable("screenId") Long screenId,
                                                  @RequestBody SeatLayoutRequest req) {
        return new ApiResponse<>(true, "Seat layout updated successfully", adminService.updateScreenLayout(screenId, req));
    }

    // --- SHOW ENDPOINTS ---
    @PostMapping("/shows")
    public ApiResponse<ShowResponse> scheduleShow(@RequestBody ShowRequest req) {
        return new ApiResponse<>(true, "Show scheduled successfully", adminService.scheduleShow(req));
    }

    @GetMapping("/shows")
    public ApiResponse<List<ShowResponse>> getShows(
            @RequestParam(value = "scope", required = false, defaultValue = "upcoming") String scope) {
        return new ApiResponse<>(true, "Shows retrieved successfully", adminService.getAllShows(scope));
    }

    @DeleteMapping("/shows/{id}")
    public ApiResponse<Void> cancelShow(@PathVariable("id") Long id) {
        adminService.cancelShow(id);
        return new ApiResponse<>(true, "Show cancelled successfully", null);
    }

    // ===== CRUD completed in Phase 2 =====

    @PutMapping("/cities/{cityId}")
    public ApiResponse<CityResponse> updateCity(@PathVariable("cityId") Long cityId,
                                               @RequestBody CityUpdateRequest req) {
        return new ApiResponse<>(true, "City updated successfully", adminService.updateCity(cityId, req));
    }

    @PutMapping("/movies/{movieId}")
    public ApiResponse<MovieResponse> updateMovie(@PathVariable("movieId") Long movieId,
                                                 @RequestBody MovieUpdateRequest req) {
        return new ApiResponse<>(true, "Movie updated successfully", adminService.updateMovie(movieId, req));
    }

    @PutMapping("/theatres/{theatreId}")
    public ApiResponse<TheatreResponse> updateTheatre(@PathVariable("theatreId") Long theatreId,
                                                     @RequestBody TheatreUpdateRequest req) {
        return new ApiResponse<>(true, "Theatre updated successfully",
                adminService.updateTheatre(theatreId, req));
    }

    @DeleteMapping("/theatres/{theatreId}")
    public ApiResponse<Void> deleteTheatre(@PathVariable("theatreId") Long theatreId) {
        adminService.deleteTheatre(theatreId);
        return new ApiResponse<>(true, "Theatre deleted successfully", null);
    }

    @PutMapping("/screens/{screenId}")
    public ApiResponse<ScreenResponse> updateScreen(@PathVariable("screenId") Long screenId,
                                                    @RequestBody ScreenUpdateRequest req) {
        return new ApiResponse<>(true, "Screen updated successfully",
                adminService.updateScreen(screenId, req));
    }

    @DeleteMapping("/screens/{screenId}")
    public ApiResponse<Void> deleteScreen(@PathVariable("screenId") Long screenId) {
        adminService.deleteScreen(screenId);
        return new ApiResponse<>(true, "Screen deleted successfully", null);
    }

    @GetMapping("/screens/{screenId}/layout")
    public ApiResponse<ScreenLayoutResponse> getScreenLayout(@PathVariable("screenId") Long screenId) {
        return new ApiResponse<>(true, "Layout retrieved", adminService.getScreenLayout(screenId));
    }

    // ===== Seat tiers, layout designer, per-show pricing (Phase 3) =====

    @GetMapping("/screens/{screenId}/tiers")
    public ApiResponse<List<SeatTierResponse>> listTiers(@PathVariable("screenId") Long screenId) {
        return new ApiResponse<>(true, "Tiers retrieved", adminService.listTiers(screenId));
    }

    @PostMapping("/screens/{screenId}/tiers")
    public ApiResponse<SeatTierResponse> createTier(@PathVariable("screenId") Long screenId,
                                                    @RequestBody SeatTierRequest req) {
        return new ApiResponse<>(true, "Seat tier created", adminService.createTier(screenId, req));
    }

    @PutMapping("/tiers/{tierId}")
    public ApiResponse<SeatTierResponse> updateTier(@PathVariable("tierId") Long tierId,
                                                    @RequestBody SeatTierRequest req) {
        return new ApiResponse<>(true, "Seat tier updated", adminService.updateTier(tierId, req));
    }

    @DeleteMapping("/tiers/{tierId}")
    public ApiResponse<Void> deleteTier(@PathVariable("tierId") Long tierId) {
        adminService.deleteTier(tierId);
        return new ApiResponse<>(true, "Seat tier deleted", null);
    }

    @GetMapping("/screens/{screenId}/seats")
    public ApiResponse<ScreenLayoutDetailResponse> getLayoutDetail(@PathVariable("screenId") Long screenId) {
        return new ApiResponse<>(true, "Layout retrieved", adminService.getLayoutDetail(screenId));
    }

    @PutMapping("/screens/{screenId}/seats")
    public ApiResponse<ScreenLayoutDetailResponse> saveLayout(@PathVariable("screenId") Long screenId,
                                                             @RequestBody LayoutSaveRequest req) {
        return new ApiResponse<>(true, "Seat layout saved", adminService.saveLayout(screenId, req));
    }

    @PutMapping("/seats/{seatId}/tier")
    public ApiResponse<ScreenSeatResponse> assignSeatTier(@PathVariable("seatId") Long seatId,
                                                         @RequestBody SeatTierAssignRequest req) {
        return new ApiResponse<>(true, "Seat tier updated", adminService.assignSeatTier(seatId, req));
    }

    @GetMapping("/shows/{showId}/prices")
    public ApiResponse<List<TierPriceResponse>> getShowPrices(@PathVariable("showId") Long showId) {
        return new ApiResponse<>(true, "Prices retrieved", adminService.getShowPrices(showId));
    }

    // ===== Booking logs (Admin can view all bookings) =====
    @GetMapping("/bookings")
    public ApiResponse<List<BookingResponse>> getAllBookings() {
        return new ApiResponse<>(true, "Bookings retrieved successfully", adminService.getAllBookings());
    }

    @GetMapping("/theatres/{theatreId}/bookings")
    public ApiResponse<List<BookingResponse>> getTheatreBookings(@PathVariable("theatreId") Long theatreId) {
        return new ApiResponse<>(true, "Theatre bookings retrieved successfully", adminService.getBookingsByTheatre(theatreId));
    }

    @GetMapping("/shows/{showId}/bookings")
    public ApiResponse<List<BookingResponse>> getShowBookings(@PathVariable("showId") Long showId) {
        return new ApiResponse<>(true, "Show bookings retrieved successfully", adminService.getBookingsByShow(showId));
    }
}
