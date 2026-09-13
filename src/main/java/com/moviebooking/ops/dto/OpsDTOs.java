package com.moviebooking.ops.dto;

import com.moviebooking.ops.model.IncidentSeverity;
import com.moviebooking.ops.model.IncidentStatus;
import com.moviebooking.ops.model.IncidentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OpsDTOs {

    // ================= SHOW REPORT =================

    public record ShowReportResponse(
        Long showId,
        String movieTitle,
        String movieLanguage,
        String movieFormat,
        String cbfcRating,
        String theatreName,
        String theatreAddress,
        String cityName,
        String screenName,
        Integer screenCapacity,
        LocalDateTime showStartTime,
        // Booking summary
        Long confirmedBookings,
        Long confirmedTickets,
        Long cancelledBookings,
        Long expiredBookings,
        BigDecimal occupancyPercentage,
        BigDecimal totalRevenue,
        // Ticket holders
        List<TicketHolderResponse> ticketHolders,
        // Meta
        String generatedAt,
        String generatedBy
    ) {}

    public record TicketHolderResponse(
        Long bookingId,
        String transactionId,
        String customerName,
        String customerPhone,
        String customerEmail,
        String seatCode,
        String seatTier,
        BigDecimal ticketPrice,
        LocalDateTime bookingTime,
        String bookingStatus,
        String paymentStatus,
        String paymentTransactionId
    ) {}

    // ================= THEATRE REPORT =================

    public record TheatreReportResponse(
        Long theatreId,
        String theatreName,
        String theatreAddress,
        String cityName,
        Integer totalScreens,
        Integer totalShows,
        Long totalSeatCapacity,
        Long confirmedTickets,
        Long confirmedBookings,
        Long cancelledBookings,
        Long expiredBookings,
        BigDecimal occupancyPercentage,
        BigDecimal totalRevenue,
        List<ShowBreakdownRow> showBreakdown,
        String dateFrom,
        String dateTo,
        String generatedAt,
        String generatedBy
    ) {}

    public record ShowBreakdownRow(
        Long showId,
        String movieTitle,
        String screenName,
        LocalDateTime startTime,
        String format,
        String language,
        Long capacity,
        Long ticketsSold,
        BigDecimal occupancyPercentage,
        BigDecimal revenue
    ) {}

    // ================= TICKET HOLDER REPORT =================

    public record TicketHolderReportResponse(
        Long showId,
        String movieTitle,
        String theatreName,
        String screenName,
        LocalDateTime showStartTime,
        Long totalConfirmedTickets,
        List<TicketHolderResponse> ticketHolders,
        String generatedAt,
        String generatedBy
    ) {}

    // ================= INCIDENTS =================

    public record CreateIncidentRequest(
        IncidentType type,
        IncidentSeverity severity,
        Long theatreId,
        Long screenId,
        Long showId,
        String description,
        LocalDateTime incidentStartTime,
        LocalDateTime reportedTime
    ) {}

    public record UpdateIncidentRequest(
        IncidentType type,
        IncidentSeverity severity,
        IncidentStatus status,
        String description
    ) {}

    public record IncidentResponse(
        Long id,
        String type,
        String severity,
        String status,
        Long theatreId,
        String theatreName,
        Long screenId,
        String screenName,
        Long showId,
        String movieTitle,
        String description,
        LocalDateTime incidentStartTime,
        LocalDateTime reportedTime,
        LocalDateTime closedAt,
        Long createdById,
        String createdByName,
        LocalDateTime createdAt
    ) {}

    // ================= REPORT SNAPSHOTS =================

    public record GenerateReportRequest(
        String reportType,   // SHOW_REPORT, THEATRE_REPORT, TICKET_HOLDER_REPORT, INCIDENT_REPORT
        Long showId,
        Long theatreId,
        Long incidentId,
        String dateFrom,
        String dateTo
    ) {}

    public record ReportSnapshotResponse(
        Long id,
        String reportType,
        Long generatedByUserId,
        String generatedByUserName,
        LocalDateTime generatedAt,
        String reportScope,
        Long scopeId,
        String scopeName,
        String filtersJson,
        String snapshotData
    ) {}

    // ================= BOOKING SUMMARY (for report snapshots) =================

    // ================= DROPDOWNS =================

    public record ShowDropdownItem(
        Long id,
        String movieTitle,
        String screenName,
        String theatreName,
        String cityName,
        LocalDateTime startTime,
        String language,
        String format,
        Long theatreId
    ) {}

    public record TheatreDropdownItem(
        Long id,
        String name,
        String cityName
    ) {}

    public record CityDropdownItem(
        Long id,
        String name
    ) {}

    // ================= BOOKING SUMMARY (for report snapshots) =================

    public record BookingSummarySnapshot(
        Long confirmedBookings,
        Long confirmedTickets,
        Long cancelledBookings,
        Long expiredBookings,
        BigDecimal totalRevenue,
        List<TicketHolderSnapshot> ticketHolders
    ) {}

    public record TicketHolderSnapshot(
        Long bookingId,
        String transactionId,
        String customerName,
        String customerPhone,
        String customerEmail,
        String seatCode,
        String seatTier,
        BigDecimal ticketPrice,
        LocalDateTime bookingTime,
        String bookingStatus,
        String paymentStatus,
        String paymentTransactionId
    ) {}
}
