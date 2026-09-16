package com.moviebooking.ops.controller;

import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.common.response.ApiResponse;
import com.moviebooking.ops.dto.*;
import com.moviebooking.ops.dto.OpsDTOs.*;
import com.moviebooking.ops.model.AuditAction;
import com.moviebooking.ops.service.AuditService;
import com.moviebooking.ops.service.IncidentService;
import com.moviebooking.ops.model.ReportSnapshot;
import com.moviebooking.ops.repository.ReportSnapshotRepository;
import com.moviebooking.ops.service.Customer360Service;
import com.moviebooking.ops.service.ExcelExportService;
import com.moviebooking.ops.service.OperationsService;
import com.moviebooking.ops.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/operations")
public class AdminOperationsController {

    private final OperationsService operationsService;
    private final IncidentService incidentService;
    private final ReportService reportService;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final ExcelExportService excelExportService;
    private final ReportSnapshotRepository reportRepository;
    private final Customer360Service customer360Service;

    public AdminOperationsController(OperationsService operationsService,
                                     IncidentService incidentService,
                                     ReportService reportService,
                                     AuditService auditService,
                                     UserRepository userRepository,
                                     ExcelExportService excelExportService,
                                     ReportSnapshotRepository reportRepository,
                                     Customer360Service customer360Service) {
        this.operationsService = operationsService;
        this.incidentService = incidentService;
        this.reportService = reportService;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.excelExportService = excelExportService;
        this.reportRepository = reportRepository;
        this.customer360Service = customer360Service;
    }

    // ================= DROPDOWNS =================

    @GetMapping("/shows")
    public ApiResponse<List<OpsDTOs.ShowDropdownItem>> getShowsDropdown(
            @RequestParam(required = false, defaultValue = "all") String scope,
            @RequestParam(required = false) Long theatreId,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long screenId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            HttpServletRequest request) {

        List<OpsDTOs.ShowDropdownItem> shows = operationsService.getFilteredShows(
                theatreId, movieId, screenId, dateFrom, dateTo);
        return ApiResponse.success("Shows loaded successfully", shows);
    }

    @GetMapping("/theatres")
    public ApiResponse<List<OpsDTOs.TheatreDropdownItem>> getTheatresDropdown(
            @RequestParam(required = false) Long cityId,
            HttpServletRequest request) {

        List<OpsDTOs.TheatreDropdownItem> theatres = operationsService.getFilteredTheatres(cityId);
        return ApiResponse.success("Theatres loaded successfully", theatres);
    }

    @GetMapping("/filter-options")
    public ApiResponse<OpsDTOs.FilterOptionsResponse> getFilterOptions(
            HttpServletRequest request) {

        List<OpsDTOs.TheatreDropdownItem> theatres = operationsService.getAllTheatres();
        List<OpsDTOs.ShowDropdownItem> allShows = operationsService.getAllShows();

        // Extract unique movies from shows
        List<OpsDTOs.MovieFilterItem> movies = allShows.stream()
                .collect(java.util.stream.Collectors.toMap(
                        OpsDTOs.ShowDropdownItem::movieTitle,
                        s -> new OpsDTOs.MovieFilterItem(null, s.movieTitle()),
                        (a, b) -> a))
                .values().stream().toList();

        // Extract unique screens from shows
        List<OpsDTOs.ScreenFilterItem> screens = allShows.stream()
                .collect(java.util.stream.Collectors.toMap(
                        s -> s.screenName() + "@" + s.theatreName(),
                        s -> new OpsDTOs.ScreenFilterItem(null, s.screenName(), s.theatreName()),
                        (a, b) -> a))
                .values().stream().toList();

        return ApiResponse.success("Filter options loaded", new OpsDTOs.FilterOptionsResponse(theatres, movies, screens));
    }

    // ================= SHOW REPORTS =================

    @GetMapping("/reports/shows/{showId}")
    public ApiResponse<ShowReportResponse> getShowReport(
            @PathVariable Long showId,
            HttpServletRequest request) {

        ShowReportResponse report = operationsService.getShowReport(showId, null); // null = admin global scope

        auditLog(AuditAction.VIEW_TICKET_HOLDERS, "SHOW", showId, null, showId,
                "Viewed show report for show #" + showId, request);

        return ApiResponse.success("Show report retrieved successfully", report);
    }

    // ================= THEATRE REPORTS =================

    @GetMapping("/reports/theatres/{theatreId}")
    public ApiResponse<TheatreReportResponse> getTheatreReport(
            @PathVariable Long theatreId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            HttpServletRequest request) {

        TheatreReportResponse report = operationsService.getTheatreReport(theatreId, dateFrom, dateTo, null);

        auditLog(AuditAction.GENERATE_REPORT, "THEATRE", theatreId, theatreId, null,
                "Viewed theatre report for theatre #" + theatreId, request);

        return ApiResponse.success("Theatre report retrieved successfully", report);
    }

    // ================= TICKET HOLDERS =================

    @GetMapping("/reports/ticket-holders")
    public ApiResponse<List<TicketHolderResponse>> getTicketHolders(
            @RequestParam Long showId,
            HttpServletRequest request) {

        List<TicketHolderResponse> holders = operationsService.getTicketHolders(showId, null);

        auditLog(AuditAction.VIEW_TICKET_HOLDERS, "SHOW", showId, null, showId,
                "Viewed " + holders.size() + " ticket holders for show #" + showId, request);

        return ApiResponse.success("Ticket holders retrieved successfully", holders);
    }

    // ================= GENERATE REPORTS =================

    @PostMapping("/reports/generate")
    public ApiResponse<ReportSnapshotResponse> generateReport(
            @RequestBody GenerateReportRequest req,
            HttpServletRequest request) {

        ReportSnapshotResponse snapshot;

        switch (req.reportType()) {
            case "SHOW_REPORT" -> snapshot = reportService.generateShowReport(req.showId(), null);
            case "THEATRE_REPORT" -> snapshot = reportService.generateTheatreReport(req.theatreId(), req.dateFrom(), req.dateTo(), null);
            case "TICKET_HOLDER_REPORT" -> snapshot = reportService.generateTicketHolderReport(req.showId(), null);
            case "INCIDENT_REPORT" -> snapshot = reportService.generateIncidentReport(req.incidentId(), null);
            default -> throw new com.moviebooking.common.exception.BusinessException("Unknown report type: " + req.reportType());
        }

        auditLog(AuditAction.GENERATE_REPORT, "REPORT", snapshot.id(), null, null,
                "Generated " + req.reportType() + " report", request);

        return ApiResponse.success("Report generated successfully", snapshot);
    }

    @GetMapping("/reports")
    public ApiResponse<List<ReportSnapshotResponse>> getReports(HttpServletRequest request) {
        List<ReportSnapshotResponse> reports = reportService.getReports(null);
        return ApiResponse.success("Reports retrieved successfully", reports);
    }

    @GetMapping("/reports/{reportId}")
    public ApiResponse<ReportSnapshotResponse> getReport(
            @PathVariable Long reportId,
            HttpServletRequest request) {

        ReportSnapshotResponse report = reportService.getReport(reportId);

        auditLog(AuditAction.VIEW_INCIDENT_REPORT, "REPORT", reportId, null, null,
                "Viewed report #" + reportId, request);

        return ApiResponse.success("Report retrieved successfully", report);
    }

    @GetMapping("/reports/{reportId}/export")
    public void exportReport(
            @PathVariable Long reportId,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        ReportSnapshot snapshot = reportRepository.findByIdAndIsDeletedFalse(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        auditLog(AuditAction.EXPORT_REPORT, "REPORT", reportId, null, null,
                "Exported report #" + reportId + " as Excel", request);

        String filename = excelExportService.getExportFilename(snapshot);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);

        try (Workbook workbook = excelExportService.generateExcel(reportId)) {
            workbook.write(response.getOutputStream());
        }
    }

    // ================= INCIDENTS =================

    @PostMapping("/incidents")
    public ApiResponse<IncidentResponse> createIncident(
            @RequestBody CreateIncidentRequest req,
            HttpServletRequest request) {

        IncidentResponse incident = incidentService.createIncident(req, null);

        auditLog(AuditAction.CREATE_INCIDENT, "INCIDENT", incident.id(),
                req.theatreId(), req.showId(),
                "Created " + req.type() + " incident for theatre #" + req.theatreId(), request);

        return ApiResponse.success("Incident created successfully", incident);
    }

    @GetMapping("/incidents")
    public ApiResponse<List<IncidentResponse>> getIncidents(
            @RequestParam(required = false) Long theatreId,
            HttpServletRequest request) {

        List<IncidentResponse> incidents = incidentService.getIncidents(theatreId, null);
        return ApiResponse.success("Incidents retrieved successfully", incidents);
    }

    @GetMapping("/incidents/{id}")
    public ApiResponse<IncidentResponse> getIncident(
            @PathVariable Long id,
            HttpServletRequest request) {

        IncidentResponse incident = incidentService.getIncident(id, null);

        auditLog(AuditAction.VIEW_INCIDENT_REPORT, "INCIDENT", id, null, null,
                "Viewed incident #" + id, request);

        return ApiResponse.success("Incident retrieved successfully", incident);
    }

    @PutMapping("/incidents/{id}")
    public ApiResponse<IncidentResponse> updateIncident(
            @PathVariable Long id,
            @RequestBody UpdateIncidentRequest req,
            HttpServletRequest request) {

        IncidentResponse incident = incidentService.updateIncident(id, req, null);

        auditLog(AuditAction.UPDATE_INCIDENT, "INCIDENT", id, null, null,
                "Updated incident #" + id, request);

        return ApiResponse.success("Incident updated successfully", incident);
    }

    @PostMapping("/incidents/{id}/close")
    public ApiResponse<IncidentResponse> closeIncident(
            @PathVariable Long id,
            HttpServletRequest request) {

        IncidentResponse incident = incidentService.closeIncident(id, null);

        auditLog(AuditAction.CLOSE_INCIDENT, "INCIDENT", id, null, null,
                "Closed incident #" + id, request);

        return ApiResponse.success("Incident closed successfully", incident);
    }

    @PostMapping("/incidents/{id}/report")
    public ApiResponse<ReportSnapshotResponse> generateIncidentReport(
            @PathVariable Long id,
            HttpServletRequest request) {

        ReportSnapshotResponse snapshot = reportService.generateIncidentReport(id, null);

        auditLog(AuditAction.GENERATE_REPORT, "INCIDENT", id, null, null,
                "Generated incident report for incident #" + id, request);

        return ApiResponse.success("Incident report generated successfully", snapshot);
    }

    // ================= CUSTOMER 360 =================

    @GetMapping("/customers/search")
    public ApiResponse<List<CustomerSearchResult>> searchCustomers(
            @RequestParam String q,
            HttpServletRequest request) {

        List<CustomerSearchResult> results = customer360Service.searchCustomers(q);

        auditLog(AuditAction.VIEW_TICKET_HOLDERS, "CUSTOMER", null, null, null,
                "Searched customers: [" + q + "] (" + results.size() + " results)", request);

        return ApiResponse.success("Customer search completed", results);
    }

    @GetMapping("/customers/{customerId}")
    public ApiResponse<CustomerProfileResponse> getCustomerProfile(
            @PathVariable Long customerId,
            HttpServletRequest request) {

        CustomerProfileResponse profile = customer360Service.getCustomerProfile(customerId);

        auditLog(AuditAction.VIEW_TICKET_HOLDERS, "CUSTOMER", customerId, null, null,
                "Viewed customer profile #" + customerId + " (" + profile.name() + ")", request);

        return ApiResponse.success("Customer profile retrieved", profile);
    }

    // ================= HELPER =================

    private void auditLog(AuditAction action, String targetType, Long targetId,
                          Long theatreId, Long showId, String details, HttpServletRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndIsDeletedFalse(email).orElse(null);
        String ip = request != null ? request.getRemoteAddr() : null;

        if (user != null) {
            auditService.log(user, action, targetType, targetId, theatreId, showId, details, ip);
        }
    }
}
