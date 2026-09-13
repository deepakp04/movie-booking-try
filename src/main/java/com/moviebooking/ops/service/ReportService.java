package com.moviebooking.ops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.ops.dto.OpsDTOs.*;
import com.moviebooking.ops.model.*;
import com.moviebooking.ops.repository.ReportSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ReportSnapshotRepository reportRepository;
    private final OperationsService operationsService;
    private final IncidentService incidentService;
    private final UserRepository userRepository;

    public ReportService(ReportSnapshotRepository reportRepository,
                         OperationsService operationsService,
                         IncidentService incidentService,
                         UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.operationsService = operationsService;
        this.incidentService = incidentService;
        this.userRepository = userRepository;
    }

    // ================= GENERATE REPORTS =================

    public ReportSnapshotResponse generateShowReport(Long showId, Long restrictToTheatreId) {
        ShowReportResponse data = operationsService.getShowReport(showId, restrictToTheatreId);
        User currentUser = getCurrentUser();

        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new BusinessException("Failed to serialize report data");
        }

        ReportSnapshot snapshot = new ReportSnapshot();
        snapshot.setReportType(ReportType.SHOW_REPORT);
        snapshot.setGeneratedBy(currentUser);
        snapshot.setGeneratedAt(LocalDateTime.now());
        snapshot.setReportScope(ReportScope.SHOW);
        snapshot.setScopeId(showId);
        snapshot.setScopeName(data.movieTitle() + " — " + data.theatreName());
        snapshot.setFiltersJson("{}");
        snapshot.setSnapshotData(snapshotJson);

        ReportSnapshot saved = reportRepository.save(snapshot);
        log.info("Show report generated for show {} by user {}", showId, currentUser.getEmail());

        return toResponse(saved);
    }

    public ReportSnapshotResponse generateTheatreReport(Long theatreId, String dateFrom, String dateTo, Long restrictToTheatreId) {
        TheatreReportResponse data = operationsService.getTheatreReport(theatreId, dateFrom, dateTo, restrictToTheatreId);
        User currentUser = getCurrentUser();

        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new BusinessException("Failed to serialize report data");
        }

        String filters = "{\"dateFrom\":\"" + (dateFrom != null ? dateFrom : "") + "\",\"dateTo\":\"" + (dateTo != null ? dateTo : "") + "\"}";

        ReportSnapshot snapshot = new ReportSnapshot();
        snapshot.setReportType(ReportType.THEATRE_REPORT);
        snapshot.setGeneratedBy(currentUser);
        snapshot.setGeneratedAt(LocalDateTime.now());
        snapshot.setReportScope(ReportScope.THEATRE);
        snapshot.setScopeId(theatreId);
        snapshot.setScopeName(data.theatreName());
        snapshot.setFiltersJson(filters);
        snapshot.setSnapshotData(snapshotJson);

        ReportSnapshot saved = reportRepository.save(snapshot);
        log.info("Theatre report generated for theatre {} by user {}", theatreId, currentUser.getEmail());

        return toResponse(saved);
    }

    public ReportSnapshotResponse generateTicketHolderReport(Long showId, Long restrictToTheatreId) {
        List<TicketHolderResponse> holders = operationsService.getTicketHolders(showId, restrictToTheatreId);
        User currentUser = getCurrentUser();

        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(holders);
        } catch (Exception e) {
            throw new BusinessException("Failed to serialize report data");
        }

        ReportSnapshot snapshot = new ReportSnapshot();
        snapshot.setReportType(ReportType.TICKET_HOLDER_REPORT);
        snapshot.setGeneratedBy(currentUser);
        snapshot.setGeneratedAt(LocalDateTime.now());
        snapshot.setReportScope(ReportScope.SHOW);
        snapshot.setScopeId(showId);
        snapshot.setScopeName("Ticket Holders — Show #" + showId);
        snapshot.setFiltersJson("{}");
        snapshot.setSnapshotData(snapshotJson);

        ReportSnapshot saved = reportRepository.save(snapshot);
        log.info("Ticket holder report generated for show {} by user {}", showId, currentUser.getEmail());

        return toResponse(saved);
    }

    public ReportSnapshotResponse generateIncidentReport(Long incidentId, Long restrictToTheatreId) {
        IncidentResponse incident = incidentService.getIncident(incidentId, restrictToTheatreId);
        User currentUser = getCurrentUser();

        // Build the full incident report data
        BookingSummarySnapshot bookingSummary = null;
        if (incident.showId() != null) {
            bookingSummary = operationsService.getBookingSummary(incident.showId());
        }

        // Combine incident + booking data
        String snapshotJson;
        try {
            var reportData = new java.util.LinkedHashMap<String, Object>();
            reportData.put("incident", incident);
            reportData.put("bookingSummary", bookingSummary);
            snapshotJson = objectMapper.writeValueAsString(reportData);
        } catch (Exception e) {
            throw new BusinessException("Failed to serialize report data");
        }

        ReportSnapshot snapshot = new ReportSnapshot();
        snapshot.setReportType(ReportType.INCIDENT_REPORT);
        snapshot.setGeneratedBy(currentUser);
        snapshot.setGeneratedAt(LocalDateTime.now());
        snapshot.setReportScope(ReportScope.INCIDENT);
        snapshot.setScopeId(incidentId);
        snapshot.setScopeName("Incident #" + incidentId + " — " + incident.type());
        snapshot.setFiltersJson("{}");
        snapshot.setSnapshotData(snapshotJson);

        ReportSnapshot saved = reportRepository.save(snapshot);
        log.info("Incident report generated for incident {} by user {}", incidentId, currentUser.getEmail());

        return toResponse(saved);
    }

    // ================= RETRIEVE REPORTS =================

    @Transactional(readOnly = true)
    public ReportSnapshotResponse getReport(Long reportId) {
        ReportSnapshot snapshot = reportRepository.findByIdAndIsDeletedFalse(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with ID: " + reportId));
        return toResponse(snapshot);
    }

    @Transactional(readOnly = true)
    public List<ReportSnapshotResponse> getReports(Long restrictToTheatreId) {
        // For now, return all reports. In production, you'd filter by theatre scope.
        List<ReportSnapshot> reports = reportRepository.findAllActive();
        return reports.stream().map(this::toResponse).toList();
    }

    // ================= HELPER =================

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private ReportSnapshotResponse toResponse(ReportSnapshot r) {
        return new ReportSnapshotResponse(
                r.getId(),
                r.getReportType().name(),
                r.getGeneratedBy().getId(),
                r.getGeneratedBy().getName(),
                r.getGeneratedAt(),
                r.getReportScope().name(),
                r.getScopeId(),
                r.getScopeName(),
                r.getFiltersJson(),
                r.getSnapshotData()
        );
    }
}
