package com.moviebooking.ops.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.ops.dto.OpsDTOs.*;
import com.moviebooking.ops.model.ReportSnapshot;
import com.moviebooking.ops.model.ReportType;
import com.moviebooking.ops.repository.ReportSnapshotRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelExportService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ENGLISH);

    private final ReportSnapshotRepository reportRepository;

    public ExcelExportService(ReportSnapshotRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * Generate an Excel workbook for a report snapshot.
     * Returns the workbook — caller is responsible for writing to response.
     */
    public Workbook generateExcel(Long reportId) {
        ReportSnapshot snapshot = reportRepository.findByIdAndIsDeletedFalse(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with ID: " + reportId));

        try {
            return switch (snapshot.getReportType()) {
                case SHOW_REPORT -> buildShowReportWorkbook(snapshot);
                case THEATRE_REPORT -> buildTheatreReportWorkbook(snapshot);
                case TICKET_HOLDER_REPORT -> buildTicketHolderWorkbook(snapshot);
                case INCIDENT_REPORT -> buildIncidentReportWorkbook(snapshot);
            };
        } catch (Exception e) {
            log.error("Failed to generate Excel for report {}", reportId, e);
            throw new BusinessException("Failed to generate Excel export");
        }
    }

    /**
     * Sanitized filename for the export.
     */
    public String getExportFilename(ReportSnapshot snapshot) {
        String timestamp = snapshot.getGeneratedAt().format(FILE_FMT);
        return switch (snapshot.getReportType()) {
            case SHOW_REPORT -> "PVR_Show_Report_" + snapshot.getScopeId() + "_" + timestamp + ".xlsx";
            case THEATRE_REPORT -> "PVR_Theatre_Report_" + snapshot.getScopeId() + "_" + timestamp + ".xlsx";
            case TICKET_HOLDER_REPORT -> "PVR_Ticket_Holders_" + snapshot.getScopeId() + "_" + timestamp + ".xlsx";
            case INCIDENT_REPORT -> "PVR_Incident_Report_" + snapshot.getScopeId() + "_" + timestamp + ".xlsx";
        };
    }

    // ================= SHOW REPORT WORKBOOK =================

    private Workbook buildShowReportWorkbook(ReportSnapshot snapshot) throws IOException {
        JsonNode data = objectMapper.readTree(snapshot.getSnapshotData());
        Workbook workbook = new XSSFWorkbook();

        // Sheet 1: Show Summary
        Sheet summary = workbook.createSheet("Show Summary");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle percentStyle = createPercentStyle(workbook);

        String[] summaryHeaders = {"Field", "Value"};
        writeHeaderRow(summary, 0, summaryHeaders, headerStyle);

        writeLabelValueRow(summary, 1, "Movie", data.path("movieTitle").asText());
        writeLabelValueRow(summary, 2, "Language", data.path("movieLanguage").asText());
        writeLabelValueRow(summary, 3, "Format", data.path("movieFormat").asText());
        writeLabelValueRow(summary, 4, "CBFC Rating", data.path("cbfcRating").asText());
        writeLabelValueRow(summary, 5, "Theatre", data.path("theatreName").asText());
        writeLabelValueRow(summary, 6, "City", data.path("cityName").asText());
        writeLabelValueRow(summary, 7, "Screen", data.path("screenName").asText());
        writeLabelValueRow(summary, 8, "Screen Capacity", data.path("screenCapacity").asInt());
        writeLabelValueRow(summary, 9, "Show Time", data.path("showStartTime").asText());
        writeLabelValueRow(summary, 10, "Confirmed Bookings", data.path("confirmedBookings").asLong());
        writeLabelValueRow(summary, 11, "Confirmed Tickets", data.path("confirmedTickets").asLong());
        writeLabelValueRow(summary, 12, "Cancelled Bookings", data.path("cancelledBookings").asLong());
        writeLabelValueRow(summary, 13, "Expired Bookings", data.path("expiredBookings").asLong());
        writeBigDecimalRow(summary, 14, "Occupancy (%)", data.path("occupancyPercentage"), percentStyle);
        writeBigDecimalRow(summary, 15, "Total Revenue", data.path("totalRevenue"), currencyStyle);
        writeLabelValueRow(summary, 16, "Generated At", data.path("generatedAt").asText());
        writeLabelValueRow(summary, 17, "Generated By", data.path("generatedBy").asText());
        autoSizeColumns(summary, 2);

        // Sheet 2: Ticket Holders
        Sheet holders = workbook.createSheet("Ticket Holders");
        String[] holderHeaders = {"Seat", "Attendee Name", "Attendee Phone", "DOB", "Booked For", "Booker Name", "Booker Email", "Tier", "Price", "Booking ID", "Booking Time", "Status", "Payment Status", "Transaction ID"};
        writeHeaderRow(holders, 0, holderHeaders, headerStyle);

        JsonNode ticketHolders = data.path("ticketHolders");
        int rowIdx = 1;
        for (JsonNode h : ticketHolders) {
            Row row = holders.createRow(rowIdx++);
            row.createCell(0).setCellValue(h.path("seatCode").asText(""));
            row.createCell(1).setCellValue(h.path("attendeeName").asText(h.path("customerName").asText("")));
            row.createCell(2).setCellValue(h.path("attendeePhone").asText(h.path("customerPhone").asText("")));
            row.createCell(3).setCellValue(h.path("attendeeDob").asText(""));
            row.createCell(4).setCellValue(h.path("bookingForSelf").asBoolean(false) ? "Self" : "Others");
            row.createCell(5).setCellValue(h.path("customerName").asText(""));
            row.createCell(6).setCellValue(h.path("customerEmail").asText(""));
            row.createCell(7).setCellValue(h.path("seatTier").asText(""));
            Cell priceCell = row.createCell(8);
            priceCell.setCellValue(h.path("ticketPrice").asDouble(0));
            priceCell.setCellStyle(currencyStyle);
            row.createCell(9).setCellValue(h.path("bookingId").asLong());
            row.createCell(10).setCellValue(h.path("bookingTime").asText(""));
            row.createCell(11).setCellValue(h.path("bookingStatus").asText(""));
            row.createCell(12).setCellValue(h.path("paymentStatus").asText(""));
            row.createCell(13).setCellValue(h.path("paymentTransactionId").asText(""));
        }
        autoSizeColumns(holders, holderHeaders.length);

        return workbook;
    }

    // ================= THEATRE REPORT WORKBOOK =================

    private Workbook buildTheatreReportWorkbook(ReportSnapshot snapshot) throws IOException {
        JsonNode data = objectMapper.readTree(snapshot.getSnapshotData());
        Workbook workbook = new XSSFWorkbook();

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle percentStyle = createPercentStyle(workbook);

        // Sheet 1: Theatre Summary
        Sheet summary = workbook.createSheet("Theatre Summary");
        writeHeaderRow(summary, 0, new String[]{"Field", "Value"}, headerStyle);
        writeLabelValueRow(summary, 1, "Theatre", data.path("theatreName").asText());
        writeLabelValueRow(summary, 2, "City", data.path("cityName").asText());
        writeLabelValueRow(summary, 3, "Address", data.path("theatreAddress").asText());
        writeLabelValueRow(summary, 4, "Total Screens", data.path("totalScreens").asInt());
        writeLabelValueRow(summary, 5, "Total Shows", data.path("totalShows").asInt());
        writeLabelValueRow(summary, 6, "Total Seat Capacity", data.path("totalSeatCapacity").asLong());
        writeLabelValueRow(summary, 7, "Confirmed Tickets", data.path("confirmedTickets").asLong());
        writeLabelValueRow(summary, 8, "Confirmed Bookings", data.path("confirmedBookings").asLong());
        writeLabelValueRow(summary, 9, "Cancelled Bookings", data.path("cancelledBookings").asLong());
        writeLabelValueRow(summary, 10, "Expired Bookings", data.path("expiredBookings").asLong());
        writeBigDecimalRow(summary, 11, "Occupancy (%)", data.path("occupancyPercentage"), percentStyle);
        writeBigDecimalRow(summary, 12, "Total Revenue", data.path("totalRevenue"), currencyStyle);
        writeLabelValueRow(summary, 13, "Period From", data.path("dateFrom").asText());
        writeLabelValueRow(summary, 14, "Period To", data.path("dateTo").asText());
        writeLabelValueRow(summary, 15, "Generated At", data.path("generatedAt").asText());
        writeLabelValueRow(summary, 16, "Generated By", data.path("generatedBy").asText());
        autoSizeColumns(summary, 2);

        // Sheet 2: Show Breakdown
        Sheet breakdown = workbook.createSheet("Show Breakdown");
        String[] bdHeaders = {"Show ID", "Movie", "Screen", "Start Time", "Format", "Language", "Capacity", "Tickets Sold", "Occupancy (%)", "Revenue"};
        writeHeaderRow(breakdown, 0, bdHeaders, headerStyle);

        JsonNode shows = data.path("showBreakdown");
        int rowIdx = 1;
        for (JsonNode s : shows) {
            Row row = breakdown.createRow(rowIdx++);
            row.createCell(0).setCellValue(s.path("showId").asLong());
            row.createCell(1).setCellValue(s.path("movieTitle").asText(""));
            row.createCell(2).setCellValue(s.path("screenName").asText(""));
            row.createCell(3).setCellValue(s.path("startTime").asText(""));
            row.createCell(4).setCellValue(s.path("format").asText(""));
            row.createCell(5).setCellValue(s.path("language").asText(""));
            row.createCell(6).setCellValue(s.path("capacity").asLong());
            row.createCell(7).setCellValue(s.path("ticketsSold").asLong());
            Cell occCell = row.createCell(8);
            occCell.setCellValue(s.path("occupancyPercentage").asDouble(0));
            occCell.setCellStyle(percentStyle);
            Cell revCell = row.createCell(9);
            revCell.setCellValue(s.path("revenue").asDouble(0));
            revCell.setCellStyle(currencyStyle);
        }
        autoSizeColumns(breakdown, bdHeaders.length);

        return workbook;
    }

    // ================= TICKET HOLDER WORKBOOK =================

    private Workbook buildTicketHolderWorkbook(ReportSnapshot snapshot) throws IOException {
        JsonNode data = objectMapper.readTree(snapshot.getSnapshotData());
        Workbook workbook = new XSSFWorkbook();

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

        Sheet sheet = workbook.createSheet("Ticket Holders");
        String[] headers = {"#", "Seat", "Attendee Name", "Attendee Phone", "DOB", "Booked For", "Booker Name", "Booker Email", "Tier", "Price", "Booking ID", "Booking Time", "Status", "Payment Status", "Transaction ID"};
        writeHeaderRow(sheet, 0, headers, headerStyle);

        int rowIdx = 1;
        int serial = 1;
        for (JsonNode h : data) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(serial++);
            row.createCell(1).setCellValue(h.path("seatCode").asText(""));
            row.createCell(2).setCellValue(h.path("attendeeName").asText(h.path("customerName").asText("")));
            row.createCell(3).setCellValue(h.path("attendeePhone").asText(h.path("customerPhone").asText("")));
            row.createCell(4).setCellValue(h.path("attendeeDob").asText(""));
            row.createCell(5).setCellValue(h.path("bookingForSelf").asBoolean(false) ? "Self" : "Others");
            row.createCell(6).setCellValue(h.path("customerName").asText(""));
            row.createCell(7).setCellValue(h.path("customerEmail").asText(""));
            row.createCell(8).setCellValue(h.path("seatTier").asText(""));
            Cell priceCell = row.createCell(9);
            priceCell.setCellValue(h.path("ticketPrice").asDouble(0));
            priceCell.setCellStyle(currencyStyle);
            row.createCell(10).setCellValue(h.path("bookingId").asLong());
            row.createCell(11).setCellValue(h.path("bookingTime").asText(""));
            row.createCell(12).setCellValue(h.path("bookingStatus").asText(""));
            row.createCell(13).setCellValue(h.path("paymentStatus").asText(""));
            row.createCell(14).setCellValue(h.path("paymentTransactionId").asText(""));
        }
        autoSizeColumns(sheet, headers.length);

        return workbook;
    }

    // ================= INCIDENT REPORT WORKBOOK =================

    private Workbook buildIncidentReportWorkbook(ReportSnapshot snapshot) throws IOException {
        JsonNode data = objectMapper.readTree(snapshot.getSnapshotData());
        Workbook workbook = new XSSFWorkbook();

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle percentStyle = createPercentStyle(workbook);

        // Sheet 1: Incident
        JsonNode incident = data.path("incident");
        Sheet incidentSheet = workbook.createSheet("Incident");
        writeHeaderRow(incidentSheet, 0, new String[]{"Field", "Value"}, headerStyle);
        writeLabelValueRow(incidentSheet, 1, "Incident ID", incident.path("id").asLong());
        writeLabelValueRow(incidentSheet, 2, "Type", incident.path("type").asText());
        writeLabelValueRow(incidentSheet, 3, "Severity", incident.path("severity").asText());
        writeLabelValueRow(incidentSheet, 4, "Status", incident.path("status").asText());
        writeLabelValueRow(incidentSheet, 5, "Theatre", incident.path("theatreName").asText());
        writeLabelValueRow(incidentSheet, 6, "Screen", incident.path("screenName").asText(""));
        writeLabelValueRow(incidentSheet, 7, "Movie", incident.path("movieTitle").asText(""));
        writeLabelValueRow(incidentSheet, 8, "Description", incident.path("description").asText());
        writeLabelValueRow(incidentSheet, 9, "Incident Start", incident.path("incidentStartTime").asText());
        writeLabelValueRow(incidentSheet, 10, "Reported At", incident.path("reportedTime").asText());
        writeLabelValueRow(incidentSheet, 11, "Closed At", incident.path("closedAt").asText(""));
        writeLabelValueRow(incidentSheet, 12, "Created By", incident.path("createdByName").asText());
        autoSizeColumns(incidentSheet, 2);

        // Sheet 2: Booking Summary
        JsonNode bookingSummary = data.path("bookingSummary");
        if (!bookingSummary.isNull()) {
            Sheet bookingSheet = workbook.createSheet("Booking Summary");
            writeHeaderRow(bookingSheet, 0, new String[]{"Field", "Value"}, headerStyle);
            writeLabelValueRow(bookingSheet, 1, "Confirmed Bookings", bookingSummary.path("confirmedBookings").asLong());
            writeLabelValueRow(bookingSheet, 2, "Confirmed Tickets", bookingSummary.path("confirmedTickets").asLong());
            writeLabelValueRow(bookingSheet, 3, "Cancelled Bookings", bookingSummary.path("cancelledBookings").asLong());
            writeLabelValueRow(bookingSheet, 4, "Expired Bookings", bookingSummary.path("expiredBookings").asLong());
            writeBigDecimalRow(bookingSheet, 5, "Total Revenue", bookingSummary.path("totalRevenue"), currencyStyle);
            autoSizeColumns(bookingSheet, 2);

            // Sheet 3: Ticket Holders
            JsonNode holders = bookingSummary.path("ticketHolders");
            if (holders.isArray() && holders.size() > 0) {
                Sheet holderSheet = workbook.createSheet("Ticket Holders");
                String[] holderHeaders = {"Seat", "Attendee Name", "Attendee Phone", "DOB", "Booked For", "Booker Name", "Booker Email", "Tier", "Price", "Booking ID", "Booking Time", "Status", "Payment Status"};
                writeHeaderRow(holderSheet, 0, holderHeaders, headerStyle);
                int rowIdx = 1;
                for (JsonNode h : holders) {
                    Row row = holderSheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(h.path("seatCode").asText(""));
                    row.createCell(1).setCellValue(h.path("attendeeName").asText(h.path("customerName").asText("")));
                    row.createCell(2).setCellValue(h.path("attendeePhone").asText(h.path("customerPhone").asText("")));
                    row.createCell(3).setCellValue(h.path("attendeeDob").asText(""));
                    row.createCell(4).setCellValue(h.path("bookingForSelf").asBoolean(false) ? "Self" : "Others");
                    row.createCell(5).setCellValue(h.path("customerName").asText(""));
                    row.createCell(6).setCellValue(h.path("customerEmail").asText(""));
                    row.createCell(7).setCellValue(h.path("seatTier").asText(""));
                    Cell priceCell = row.createCell(8);
                    priceCell.setCellValue(h.path("ticketPrice").asDouble(0));
                    priceCell.setCellStyle(currencyStyle);
                    row.createCell(9).setCellValue(h.path("bookingId").asLong());
                    row.createCell(10).setCellValue(h.path("bookingTime").asText(""));
                    row.createCell(11).setCellValue(h.path("bookingStatus").asText(""));
                    row.createCell(12).setCellValue(h.path("paymentStatus").asText(""));
                }
                autoSizeColumns(holderSheet, holderHeaders.length);
            }
        }

        return workbook;
    }

    // ================= CELL STYLE HELPERS =================

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("₹#,##0.00"));
        return style;
    }

    private CellStyle createPercentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.0%"));
        return style;
    }

    // ================= ROW HELPERS =================

    private void writeHeaderRow(Sheet sheet, int rowIdx, String[] headers, CellStyle headerStyle) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeLabelValueRow(Sheet sheet, int rowIdx, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value != null ? value : "");
    }

    private void writeLabelValueRow(Sheet sheet, int rowIdx, String label, long value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private void writeLabelValueRow(Sheet sheet, int rowIdx, String label, int value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private void writeBigDecimalRow(Sheet sheet, int rowIdx, String label, JsonNode valueNode, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        Cell cell = row.createCell(1);
        BigDecimal val = new BigDecimal(valueNode.asText("0"));
        cell.setCellValue(val.doubleValue());
        cell.setCellStyle(style);
    }

    private void autoSizeColumns(Sheet sheet, int numCols) {
        for (int i = 0; i < numCols; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
