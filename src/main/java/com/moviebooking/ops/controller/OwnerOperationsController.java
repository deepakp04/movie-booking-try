package com.moviebooking.ops.controller;

import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.catalog.model.Theatre;
import com.moviebooking.catalog.repository.TheatreRepository;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.common.response.ApiResponse;
import com.moviebooking.ops.dto.OpsDTOs.*;
import com.moviebooking.ops.model.AuditAction;
import com.moviebooking.ops.service.AuditService;
import com.moviebooking.ops.service.IncidentService;
import com.moviebooking.ops.model.ReportSnapshot;
import com.moviebooking.ops.repository.ReportSnapshotRepository;
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
@RequestMapping("/api/owner/operations")
public class OwnerOperationsController {

    private final OperationsService operationsService;
    private final IncidentService incidentService;
    private final ReportService reportService;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final TheatreRepository theatreRepository;
    private final ExcelExportService excelExportService;
    private final ReportSnapshotRepository reportRepository;

    public OwnerOperationsController(OperationsService operationsService,
                                     IncidentService incidentService,
                                     ReportService reportService,
                                     AuditService auditService,
                                     UserRepository userRepository,
                                     TheatreRepository theatreRepository,
                                     ExcelExportService excelExportService,
                                     ReportSnapshotRepository reportRepository) {
        this.operationsService = operationsService;
        this.incidentService = incidentService;
        this.reportService = reportService;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.theatreRepository = theatreRepository;
        this.excelExportService = excelExportService;
        this.reportRepository = reportRepository;
    }

    /**
     * Resolve the theatre owned by the currently authenticated user.
     * This is the single choke point — never trust a client-provided theatre ID.
     */
    private Theatre currentOwnersTheatre() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
        return theatreRepository.findByOwnerIdAndIsDeletedFalse(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No theatre is currently assigned to your account. Contact an administrator."));
    }

    // ================= DROPDOWNS =================

    @GetMapping("/shows")
    public ApiResponse<List<OpsDTOs.ShowDropdownItem>> getShowsDropdown(
            @RequestParam(required = false, defaultValue = "all") String scope,
            HttpServletRequest request) {

        Theatre theatre = currentOwnersTheatre();
        List<OpsDTOs.ShowDropdownItem> shows = operationsService.getShowsByTheatre(theatre.getId());
        return ApiResponse.success("Shows loaded successfully", shows);
    }

    @GetMapping("/theatres")
    public ApiResponse<OpsDTOs.TheatreDropdownItem> getMyTheatre(
            HttpServletRequest request) {

        Theatre theatre = currentOwnersTheatre();
        OpsDTOs.TheatreDropdownItem item = new OpsDTOs.TheatreDropdownItem(
                theatre.getId(), theatre.getName(),
                theatre.getCity() != null ? theatre.getCity().getName() : ""
        );
        return ApiResponse.success("Theatre loaded successfully", item);
    }

    // ================= SHOW REPORTS =================

    @GetMapping("/reports/shows/{showId}")
    public ApiResponse<ShowReportResponse> getShowReport(
            @PathVariable Long showId,
            HttpServletRequest request) {

        Theatre theatre = currentOwnersTheatre();
        ShowReportResponse report = operationsService.getShowReport(showId, theatre.getId());

        auditLog(AuditAction.VIEW_TICKET_HOLDERS, "SHOW", showId, theatre.getId(), showId,
                "Viewed show report for show #" + showId, request);

        return ApiResponse.success("Show report retrieved successfully", report);
    }

    // ================= THEATRE REPORTS =================

    @GetMapping("/reports/theatre")
    public ApiResponse<TheatreReportResponse> getMyTheatreReport(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            HttpServletRequest request) {

        Theatre theatre = currentOwnersTheatre();
        TheatreReportResponse report = operationsService.getTheatreReport(theatre.getId(), dateFrom, dateTo, theatre.getId());

        auditLog(AuditAction.GENERATE_REPORT, "THEATRE", theatre.getId(), theatre.getId(), null,
                "Viewed theatre report", request);

        return ApiResponse.success("Theatre report retrieved successfully", report);
    }

    // ================= TICKET HOLDERS =================

    @GetMapping("/reports/ticket-holders")
    public ApiResponse<List<TicketHolderResponse>> getTicketHolders(
            @RequestParam Long showId,
            HttpServletRequest request) {

        Theatre theatre = currentOwnersTheatre();
        List<TicketHolderResponse> holders = operationsService.getTicketHolders(showId, theatre.getId());

        auditLog(AuditAction.VIEW_TICKET_HOLDERS, "SHOW", showId, theatre.getId(), showId,
                "Viewed " + holders.size() + " ticket holders for show #" + showId, request);

        return ApiResponse.success("Ticket holders retrieved successfully", holders);
    }

    // ================= GENERATE REPORTS =================

    @PostMapping("/reports/generate")
    public ApiResponse<ReportSnapshotResponse> generateReport(
            @RequestBody GenerateReportRequest req,
            HttpServletRequest request) {

        Theatre theatre = currentOwnersTheatre();
        ReportSnapshotResponse snapshot;

        switch (req.reportType()) {
            case "SHOW_REPORT" -> snapshot = reportService.generateShowReport(req.showId(), theatre.getId());
            case "THEATRE_REPORT" -> snapshot = reportService.generateTheatreReport(theatre.getId(), req.dateFrom(), req.dateTo(), theatre.getId());
            case "TICKET_HOLDER_REPORT" -> snapshot = reportService.generateTicketHolderReport(req.showId(), theatre.getId());
            case "INCIDENT_REPORT" -> snapshot = reportService.generateIncidentReport(req.incidentId(), theatre.getId());
            default -> throw new com.moviebooking.common.exception.BusinessException("Unknown report type: " + req.reportType());
        }

        auditLog(AuditAction.GENERATE_REPORT, "REPORT", snapshot.id(), theatre.getId(), null,
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

        Theatre theatre = currentOwnersTheatre();
        // Force the theatre to be the owner's theatre — ignore any theatreId from the request
        CreateIncidentRequest scopedReq = new CreateIncidentRequest(
                req.type(), req.severity(), theatre.getId(), req.screenId(), req.showId(),
                req.description(), req.incidentStartTime(), req.reportedTime()
        );

        IncidentResponse incident = incidentService.createIncident(scopedReq, theatre.getId());

        auditLog(AuditAction.CREATE_INCIDENT, "INCIDENT", incident.id(),
                theatre.getId(), req.showId(),
                "Created " + req.type() + " incident", request);

        return ApiResponse.success("Incident created successfully", incident);
    }

    @GetMapping("/incidents")
    public ApiResponse<List<IncidentResponse>> getIncidents(HttpServletRequest request) {
        Theatre theatre = currentOwnersTheatre();
        List<IncidentResponse> incidents = incidentService.getIncidents(theatre.getId(), theatre.getId());
        return ApiResponse.success("Incidents retrieved successfully", incidents);
    }

    @GetMapping("/incidents/{id}")
    public ApiResponse<IncidentResponse> getIncident(
            @PathVariable Long id,
            HttpServletRequest request) {

        Theatre theatre = currentOwnersTheatre();
        IncidentResponse incident = incidentService.getIncident(id, theatre.getId());

        auditLog(AuditAction.VIEW_INCIDENT_REPORT, "INCIDENT", id, theatre.getId(), null,
                "Viewed incident #" + id, request);

        return ApiResponse.success("Incident retrieved successfully", incident);
    }

    @PutMapping("/incidents/{id}")
    public ApiResponse<IncidentResponse> updateIncident(
            @PathVariable Long id,
            @RequestBody UpdateIncidentRequest req,
            HttpServletRequest request) {

        Theatre theatre = currentOwnersTheatre();
        IncidentResponse incident = incidentService.updateIncident(id, req, theatre.getId());

        auditLog(AuditAction.UPDATE_INCIDENT, "INCIDENT", id, theatre.getId(), null,
                "Updated incident #" + id, request);

        return ApiResponse.success("Incident updated successfully", incident);
    }

    @PostMapping("/incidents/{id}/close")
    public ApiResponse<IncidentResponse> closeIncident(
            @PathVariable Long id,
            HttpServletRequest request) {

        Theatre theatre = currentOwnersTheatre();
        IncidentResponse incident = incidentService.closeIncident(id, theatre.getId());

        auditLog(AuditAction.CLOSE_INCIDENT, "INCIDENT", id, theatre.getId(), null,
                "Closed incident #" + id, request);

        return ApiResponse.success("Incident closed successfully", incident);
    }

    @PostMapping("/incidents/{id}/report")
    public ApiResponse<ReportSnapshotResponse> generateIncidentReport(
            @PathVariable Long id,
            HttpServletRequest request) {

        Theatre theatre = currentOwnersTheatre();
        ReportSnapshotResponse snapshot = reportService.generateIncidentReport(id, theatre.getId());

        auditLog(AuditAction.GENERATE_REPORT, "INCIDENT", id, theatre.getId(), null,
                "Generated incident report for incident #" + id, request);

        return ApiResponse.success("Incident report generated successfully", snapshot);
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
