package com.moviebooking.mail.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviebooking.auth.entity.User;
import com.moviebooking.booking.model.Booking;
import com.moviebooking.booking.model.ShowSeat;
import com.moviebooking.booking.repository.ShowSeatRepository;
import com.moviebooking.catalog.model.CbfcRating;
import com.moviebooking.catalog.model.Show;
import com.moviebooking.payment.model.PaymentTransaction;
import com.moviebooking.payment.repository.PaymentTransactionRepository;
import com.moviebooking.mail.model.EmailOutbox;
import com.moviebooking.mail.model.EmailOutbox.EmailType;
import com.moviebooking.mail.repository.EmailOutboxRepository;

@Service
public class BookingEmailService {

    private static final Logger log = LoggerFactory.getLogger(BookingEmailService.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH);

    private final EmailOutboxRepository outboxRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PaymentTransactionRepository paymentRepository;

    public BookingEmailService(EmailOutboxRepository outboxRepository,
                               ShowSeatRepository showSeatRepository,
                               PaymentTransactionRepository paymentRepository) {
        this.outboxRepository = outboxRepository;
        this.showSeatRepository = showSeatRepository;
        this.paymentRepository = paymentRepository;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Queue a booking confirmation email. Idempotent — won't duplicate if already queued/sent.
     */
    @Transactional
    public void queueConfirmationEmail(Booking booking) {
        // Duplicate prevention: check if already queued or sent
        if (outboxRepository.existsByBookingIdAndEmailTypeAndStatusIn(
                booking.getId(),
                EmailType.BOOKING_CONFIRMED,
                List.of(EmailOutbox.EmailStatus.PENDING, EmailOutbox.EmailStatus.SENT))) {
            log.info("Confirmation email already queued/sent for booking {}, skipping", booking.getId());
            return;
        }

        String html = buildConfirmationHtml(booking);
        String subject = "PVR Cinemas — Booking Confirmed #" + formatBookingId(booking);

        EmailOutbox outbox = new EmailOutbox();
        outbox.setBookingId(booking.getId());
        outbox.setEmailType(EmailType.BOOKING_CONFIRMED);
        outbox.setRecipientEmail(booking.getUser().getEmail());
        outbox.setSubject(subject);
        outbox.setHtmlBody(html);
        outbox.setStatus(EmailOutbox.EmailStatus.PENDING);
        outboxRepository.save(outbox);

        log.info("Confirmation email queued for booking {} → {}", booking.getId(), booking.getUser().getEmail());
    }

    /**
     * Queue a booking cancellation email.
     */
    @Transactional
    public void queueCancellationEmail(Booking booking) {
        // Duplicate prevention
        if (outboxRepository.existsByBookingIdAndEmailTypeAndStatusIn(
                booking.getId(),
                EmailType.BOOKING_CANCELLED,
                List.of(EmailOutbox.EmailStatus.PENDING, EmailOutbox.EmailStatus.SENT))) {
            log.info("Cancellation email already queued/sent for booking {}, skipping", booking.getId());
            return;
        }

        String html = buildCancellationHtml(booking);
        String subject = "PVR Cinemas — Booking Cancelled #" + formatBookingId(booking);

        EmailOutbox outbox = new EmailOutbox();
        outbox.setBookingId(booking.getId());
        outbox.setEmailType(EmailType.BOOKING_CANCELLED);
        outbox.setRecipientEmail(booking.getUser().getEmail());
        outbox.setSubject(subject);
        outbox.setHtmlBody(html);
        outbox.setStatus(EmailOutbox.EmailStatus.PENDING);
        outboxRepository.save(outbox);

        log.info("Cancellation email queued for booking {} → {}", booking.getId(), booking.getUser().getEmail());
    }

    // ------------------------------------------------------------------
    // Confirmation Email HTML
    // ------------------------------------------------------------------

    private String buildConfirmationHtml(Booking booking) {
        Show show = booking.getShow();
        User user = booking.getUser();
        PaymentTransaction tx = paymentRepository.findByBookingId(booking.getId()).orElse(null);

        // Fetch seat details with price snapshots
        List<ShowSeat> seats = showSeatRepository.findByBookingIdAndStatusForUpdate(
                booking.getId(), com.moviebooking.booking.model.SeatStatus.BOOKED);
        // Fallback: if seats are still HELD (race condition), fetch by booking ID
        if (seats.isEmpty()) {
            seats = showSeatRepository.findByShowId(show.getId()).stream()
                    .filter(s -> booking.getId().equals(s.getBookingId()))
                    .toList();
        }

        String bookingId = formatBookingId(booking);
        String bookedOn = booking.getCreatedAt() != null
                ? booking.getCreatedAt().format(DATETIME_FORMAT) : "N/A";
        String showDate = show.getStartTime() != null ? show.getStartTime().format(DATE_FORMAT) : "N/A";
        String showTime = show.getStartTime() != null ? show.getStartTime().format(TIME_FORMAT) : "N/A";

        String movieTitle = show.getMovie().getTitle();
        String language = show.getLanguage() != null ? capitalize(show.getLanguage().name()) : "";
        String format = show.getFormat() != null ? show.getFormat().getValue() : "";
        String movieInfo = language + (format.isEmpty() ? "" : " • " + format);

        CbfcRating rating = show.getMovie().getCbfcRating();
        String ratingBadge = rating != null ? rating.name() : "";

        String theatreName = show.getScreen().getTheatre().getName();
        String screenName = show.getScreen().getName();
        String theatreAddress = show.getScreen().getTheatre().getAddress();
        String cityName = show.getScreen().getTheatre().getCity().getName();

        // Seat rows
        StringBuilder seatRows = new StringBuilder();
        for (ShowSeat s : seats) {
            seatRows.append(String.format(
                "<tr><td style='padding:10px 16px;border-bottom:1px solid #2a2e37;color:#e4e4e7;font-weight:600;'>%s</td>" +
                "<td style='padding:10px 16px;border-bottom:1px solid #2a2e37;color:#a1a1aa;'>%s</td>" +
                "<td style='padding:10px 16px;border-bottom:1px solid #2a2e37;color:#e5b80b;text-align:right;'>%s</td></tr>",
                escapeHtml(s.getSeatCode()),
                escapeHtml(s.getTierName() != null ? s.getTierName() : "Standard"),
                s.getPrice() != null ? "₹" + s.getPrice() : "₹0"
            ));
        }

        // Payment info
        String paymentStatus = tx != null ? capitalize(tx.getStatus().name()) : "N/A";
        String paymentMethod = "Online Payment";
        String transactionId = booking.getTransactionId();
        String paymentId = tx != null && tx.getRazorpayPaymentId() != null ? tx.getRazorpayPaymentId() : "N/A";

        // Customer info
        String customerName = escapeHtml(user.getName());
        String customerEmail = escapeHtml(user.getEmail());
        String customerPhone = user.getPhone() != null ? user.getPhone() : "Not provided";

        String ticketPlural = booking.getNumberOfSeats() > 1 ? "s" : "";

        return String.format(confirmationTemplate(),
                // Header: bookingId, bookedOn
                bookingId, bookedOn,
                // Movie: title, info, ratingBadge, showDate, showTime
                escapeHtml(movieTitle), movieInfo, ratingBadge, showDate, showTime,
                // Theatre: name, screen, address, city
                escapeHtml(theatreName), escapeHtml(screenName), escapeHtml(theatreAddress), escapeHtml(cityName),
                // Seats
                seatRows.toString(),
                // Ticket count + plural
                booking.getNumberOfSeats(), ticketPlural,
                // Payment: total, status, method, transactionId, paymentId
                booking.getTotalAmount(), paymentStatus, paymentMethod, transactionId, paymentId,
                // Digital ticket: bookingId, movie, date, time, theatre, screen
                bookingId, escapeHtml(movieTitle), showDate, showTime, escapeHtml(theatreName), escapeHtml(screenName),
                // Customer: name, email, phone
                customerName, customerEmail, customerPhone,
                // Support section: bookingId
                bookingId
        );
    }

    // ------------------------------------------------------------------
    // Cancellation Email HTML
    // ------------------------------------------------------------------

    private String buildCancellationHtml(Booking booking) {
        Show show = booking.getShow();
        User user = booking.getUser();

        // Fetch seat details
        List<ShowSeat> seats = showSeatRepository.findByShowId(show.getId()).stream()
                .filter(s -> booking.getId().equals(s.getBookingId()))
                .toList();

        String bookingId = formatBookingId(booking);
        String cancelledOn = LocalDateTime.now().format(DATETIME_FORMAT);
        String showDate = show.getStartTime() != null ? show.getStartTime().format(DATE_FORMAT) : "N/A";
        String showTime = show.getStartTime() != null ? show.getStartTime().format(TIME_FORMAT) : "N/A";

        String movieTitle = show.getMovie().getTitle();
        String language = show.getLanguage() != null ? capitalize(show.getLanguage().name()) : "";
        String format = show.getFormat() != null ? show.getFormat().getValue() : "";
        String movieInfo = language + (format.isEmpty() ? "" : " • " + format);

        String theatreName = show.getScreen().getTheatre().getName();
        String screenName = show.getScreen().getName();
        String theatreAddress = show.getScreen().getTheatre().getAddress();
        String cityName = show.getScreen().getTheatre().getCity().getName();

        // Cancelled seat rows
        StringBuilder seatRows = new StringBuilder();
        for (ShowSeat s : seats) {
            seatRows.append(String.format(
                "<tr><td style='padding:10px 16px;border-bottom:1px solid #2a2e37;color:#e4e4e7;font-weight:600;'>%s</td>" +
                "<td style='padding:10px 16px;border-bottom:1px solid #2a2e37;color:#a1a1aa;'>%s</td></tr>",
                escapeHtml(s.getSeatCode()),
                escapeHtml(s.getTierName() != null ? s.getTierName() : "Standard")
            ));
        }

        // Payment info
        String transactionId = booking.getTransactionId();

        return String.format(cancellationTemplate(),
                bookingId, cancelledOn,
                escapeHtml(movieTitle), movieInfo, showDate, showTime,
                escapeHtml(theatreName), escapeHtml(screenName), escapeHtml(theatreAddress), escapeHtml(cityName),
                seatRows.toString(),
                booking.getTotalAmount(),
                transactionId,
                escapeHtml(user.getName())
        );
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String formatBookingId(Booking booking) {
        return "PVR-" + String.format("%08d", booking.getId());
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------------
    // HTML Templates
    // ------------------------------------------------------------------

    private String confirmationTemplate() {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
        <body style="margin:0;padding:0;background-color:#0d0f12;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
        <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0d0f12;padding:20px 0;">
        <tr><td align="center">
        <table width="600" cellpadding="0" cellspacing="0" style="background-color:#16191e;border-radius:8px;overflow:hidden;border:1px solid #2a2e37;">

        <!-- Header -->
        <tr><td style="background:linear-gradient(135deg,#1a1d23,#0d0f12);padding:32px 40px;text-align:center;border-bottom:2px solid #e5b80b;">
            <h1 style="margin:0;font-size:28px;color:#ffffff;letter-spacing:3px;">PVR <span style="color:#e5b80b;">CINEMAS</span></h1>
            <div style="margin-top:16px;display:inline-block;background:rgba(46,125,50,0.15);border:1px solid #2e7d32;border-radius:20px;padding:8px 24px;">
                <span style="color:#b9f6ca;font-size:14px;font-weight:600;">✓ BOOKING CONFIRMED</span>
            </div>
            <p style="margin:12px 0 0;color:#a1a1aa;font-size:13px;">Booking ID: <strong style="color:#e5b80b;">%s</strong></p>
            <p style="margin:4px 0 0;color:#71717a;font-size:12px;">Booked on: %s</p>
        </td></tr>

        <!-- Movie Info -->
        <tr><td style="padding:28px 40px;">
            <h2 style="margin:0 0 8px;color:#e4e4e7;font-size:22px;">%s</h2>
            <p style="margin:0 0 4px;color:#a1a1aa;font-size:14px;">%s</p>
            %s<p style="margin:0;color:#a1a1aa;font-size:14px;">%s</p>
            <p style="margin:4px 0 0;color:#e5b80b;font-size:18px;font-weight:700;">%s</p>
        </td></tr>

        <!-- Theatre Info -->
        <tr><td style="padding:0 40px 24px;">
            <h3 style="margin:0 0 8px;color:#71717a;font-size:11px;letter-spacing:2px;text-transform:uppercase;">Theatre</h3>
            <p style="margin:0;color:#e4e4e7;font-size:15px;font-weight:600;">%s</p>
            <p style="margin:4px 0;color:#a1a1aa;font-size:13px;">%s</p>
            <p style="margin:0;color:#71717a;font-size:13px;">%s, %s</p>
        </td></tr>

        <!-- Seats -->
        <tr><td style="padding:0 40px 24px;">
            <h3 style="margin:0 0 12px;color:#71717a;font-size:11px;letter-spacing:2px;text-transform:uppercase;">Your Seats</h3>
            <table width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;background:#0b0c0e;border-radius:6px;overflow:hidden;">
            <tr style="border-bottom:2px solid #2a2e37;">
                <td style="padding:10px 16px;color:#71717a;font-size:11px;text-transform:uppercase;letter-spacing:1px;">Seat</td>
                <td style="padding:10px 16px;color:#71717a;font-size:11px;text-transform:uppercase;letter-spacing:1px;">Category</td>
                <td style="padding:10px 16px;color:#71717a;font-size:11px;text-transform:uppercase;letter-spacing:1px;text-align:right;">Price</td>
            </tr>
            %s
            </table>
            <p style="margin:12px 0 0;color:#a1a1aa;font-size:14px;"><strong>%d</strong> Ticket%s</p>
        </td></tr>

        <!-- Payment Summary -->
        <tr><td style="padding:0 40px 24px;">
            <h3 style="margin:0 0 12px;color:#71717a;font-size:11px;letter-spacing:2px;text-transform:uppercase;">Payment Summary</h3>
            <div style="background:#0b0c0e;border-radius:6px;padding:16px;">
                <table width="100%%" cellpadding="0" cellspacing="0">
                <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Total Paid</td>
                    <td style="padding:4px 0;color:#e5b80b;font-size:16px;font-weight:700;text-align:right;">₹%s</td></tr>
                <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Payment Status</td>
                    <td style="padding:4px 0;color:#b9f6ca;font-size:13px;text-align:right;">%s</td></tr>
                <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Payment Method</td>
                    <td style="padding:4px 0;color:#e4e4e7;font-size:13px;text-align:right;">%s</td></tr>
                <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Transaction ID</td>
                    <td style="padding:4px 0;color:#e4e4e7;font-size:12px;text-align:right;word-break:break-all;">%s</td></tr>
                <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Payment ID</td>
                    <td style="padding:4px 0;color:#e4e4e7;font-size:12px;text-align:right;word-break:break-all;">%s</td></tr>
                </table>
            </div>
        </td></tr>

        <!-- Digital Ticket -->
        <tr><td style="padding:0 40px 24px;">
            <div style="border:2px dashed #e5b80b;border-radius:8px;padding:24px;text-align:center;">
                <h3 style="margin:0 0 16px;color:#e5b80b;font-size:12px;letter-spacing:2px;text-transform:uppercase;">Your Ticket</h3>
                <p style="margin:0 0 8px;color:#71717a;font-size:11px;letter-spacing:1px;">BOOKING ID</p>
                <p style="margin:0 0 16px;color:#e5b80b;font-size:22px;font-weight:700;letter-spacing:2px;">%s</p>
                <table width="100%%" cellpadding="0" cellspacing="0" style="text-align:left;">
                <tr><td style="padding:4px 0;color:#71717a;font-size:11px;width:80px;vertical-align:top;">MOVIE</td>
                    <td style="padding:4px 0;color:#e4e4e7;font-size:13px;">%s</td></tr>
                <tr><td style="padding:4px 0;color:#71717a;font-size:11px;vertical-align:top;">DATE</td>
                    <td style="padding:4px 0;color:#e4e4e7;font-size:13px;">%s</td></tr>
                <tr><td style="padding:4px 0;color:#71717a;font-size:11px;vertical-align:top;">TIME</td>
                    <td style="padding:4px 0;color:#e4e4e7;font-size:13px;">%s</td></tr>
                <tr><td style="padding:4px 0;color:#71717a;font-size:11px;vertical-align:top;">THEATRE</td>
                    <td style="padding:4px 0;color:#e4e4e7;font-size:13px;">%s</td></tr>
                <tr><td style="padding:4px 0;color:#71717a;font-size:11px;vertical-align:top;">SCREEN</td>
                    <td style="padding:4px 0;color:#e4e4e7;font-size:13px;">%s</td></tr>
                </table>
            </div>
        </td></tr>

        <!-- Customer Info -->
        <tr><td style="padding:0 40px 24px;">
            <h3 style="margin:0 0 8px;color:#71717a;font-size:11px;letter-spacing:2px;text-transform:uppercase;">Booked By</h3>
            <p style="margin:0;color:#e4e4e7;font-size:14px;">%s</p>
            <p style="margin:4px 0;color:#a1a1aa;font-size:13px;">%s</p>
            <p style="margin:0;color:#a1a1aa;font-size:13px;">%s</p>
        </td></tr>

        <!-- Important Info -->
        <tr><td style="padding:0 40px 24px;">
            <h3 style="margin:0 0 8px;color:#71717a;font-size:11px;letter-spacing:2px;text-transform:uppercase;">Important Information</h3>
            <ul style="margin:0;padding-left:18px;color:#a1a1aa;font-size:13px;line-height:1.8;">
                <li>Please arrive at the theatre before the show starts.</li>
                <li>Keep your booking confirmation available.</li>
                <li>Carry valid identification where required.</li>
                <li>Tickets are subject to the applicable booking and cancellation policy.</li>
            </ul>
        </td></tr>

        <!-- Support -->
        <tr><td style="padding:0 40px 32px;">
            <div style="background:#0b0c0e;border-radius:6px;padding:16px;text-align:center;">
                <p style="margin:0 0 4px;color:#71717a;font-size:11px;letter-spacing:1px;text-transform:uppercase;">Need Help?</p>
                <p style="margin:0;color:#a1a1aa;font-size:13px;">Booking ID: <strong style="color:#e5b80b;">%s</strong></p>
                <p style="margin:8px 0 0;color:#71717a;font-size:12px;">Please mention your Booking ID when contacting customer support regarding this booking.</p>
            </div>
        </td></tr>

        <!-- Footer -->
        <tr><td style="background:#0b0c0e;padding:16px 40px;text-align:center;border-top:1px solid #2a2e37;">
            <p style="margin:0;color:#52525b;font-size:11px;">PVR Cinemas — Movie Booking System</p>
        </td></tr>

        </table>
        </td></tr>
        </table>
        </body></html>
        """;
    }

    private String cancellationTemplate() {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
        <body style="margin:0;padding:0;background-color:#0d0f12;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
        <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0d0f12;padding:20px 0;">
        <tr><td align="center">
        <table width="600" cellpadding="0" cellspacing="0" style="background-color:#16191e;border-radius:8px;overflow:hidden;border:1px solid #2a2e37;">

        <!-- Header -->
        <tr><td style="background:linear-gradient(135deg,#1a1d23,#0d0f12);padding:32px 40px;text-align:center;border-bottom:2px solid #e53935;">
            <h1 style="margin:0;font-size:28px;color:#ffffff;letter-spacing:3px;">PVR <span style="color:#e5b80b;">CINEMAS</span></h1>
            <div style="margin-top:16px;display:inline-block;background:rgba(229,57,53,0.15);border:1px solid #e53935;border-radius:20px;padding:8px 24px;">
                <span style="color:#ff8a80;font-size:14px;font-weight:600;">BOOKING CANCELLED</span>
            </div>
            <p style="margin:12px 0 0;color:#a1a1aa;font-size:13px;">Your booking has been cancelled successfully.</p>
            <p style="margin:8px 0 0;color:#a1a1aa;font-size:13px;">Booking ID: <strong style="color:#e5b80b;">%s</strong></p>
            <p style="margin:4px 0 0;color:#71717a;font-size:12px;">Cancelled on: %s</p>
        </td></tr>

        <!-- Movie Info -->
        <tr><td style="padding:28px 40px;">
            <h2 style="margin:0 0 8px;color:#e4e4e7;font-size:22px;">%s</h2>
            <p style="margin:0 0 4px;color:#a1a1aa;font-size:14px;">%s</p>
            <p style="margin:0;color:#a1a1aa;font-size:14px;">%s</p>
            <p style="margin:4px 0 0;color:#e5b80b;font-size:18px;font-weight:700;">%s</p>
        </td></tr>

        <!-- Theatre Info -->
        <tr><td style="padding:0 40px 24px;">
            <h3 style="margin:0 0 8px;color:#71717a;font-size:11px;letter-spacing:2px;text-transform:uppercase;">Theatre</h3>
            <p style="margin:0;color:#e4e4e7;font-size:15px;font-weight:600;">%s</p>
            <p style="margin:4px 0;color:#a1a1aa;font-size:13px;">%s</p>
            <p style="margin:0;color:#71717a;font-size:13px;">%s, %s</p>
        </td></tr>

        <!-- Cancelled Seats -->
        <tr><td style="padding:0 40px 24px;">
            <h3 style="margin:0 0 12px;color:#71717a;font-size:11px;letter-spacing:2px;text-transform:uppercase;">Cancelled Seats</h3>
            <table width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;background:#0b0c0e;border-radius:6px;overflow:hidden;">
            <tr style="border-bottom:2px solid #2a2e37;">
                <td style="padding:10px 16px;color:#71717a;font-size:11px;text-transform:uppercase;letter-spacing:1px;">Seat</td>
                <td style="padding:10px 16px;color:#71717a;font-size:11px;text-transform:uppercase;letter-spacing:1px;">Category</td>
            </tr>
            %s
            </table>
        </td></tr>

        <!-- Cancellation Summary -->
        <tr><td style="padding:0 40px 24px;">
            <h3 style="margin:0 0 12px;color:#71717a;font-size:11px;letter-spacing:2px;text-transform:uppercase;">Cancellation Summary</h3>
            <div style="background:#0b0c0e;border-radius:6px;padding:16px;">
                <table width="100%%" cellpadding="0" cellspacing="0">
                <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Amount Paid</td>
                    <td style="padding:4px 0;color:#e4e4e7;font-size:14px;text-align:right;">₹%s</td></tr>
                <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Refund Amount</td>
                    <td style="padding:4px 0;color:#a1a1aa;font-size:14px;text-align:right;">₹0</td></tr>
                <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Refund Status</td>
                    <td style="padding:4px 0;color:#a1a1aa;font-size:13px;text-align:right;">Not Applicable</td></tr>
                <tr><td style="padding:8px 0 0;color:#a1a1aa;font-size:13px;border-top:1px solid #2a2e37;">Transaction ID</td>
                    <td style="padding:8px 0 0;color:#e4e4e7;font-size:12px;text-align:right;border-top:1px solid #2a2e37;word-break:break-all;">%s</td></tr>
                </table>
            </div>
        </td></tr>

        <!-- Important Info -->
        <tr><td style="padding:0 40px 24px;">
            <h3 style="margin:0 0 8px;color:#71717a;font-size:11px;letter-spacing:2px;text-transform:uppercase;">Important Information</h3>
            <ul style="margin:0;padding-left:18px;color:#a1a1aa;font-size:13px;line-height:1.8;">
                <li>Your seats have been released and are available for other customers.</li>
                <li>If applicable, refund processing is handled separately via the payment provider.</li>
                <li>For any queries, please contact customer support with your Booking ID.</li>
            </ul>
        </td></tr>

        <!-- Support -->
        <tr><td style="padding:0 40px 32px;">
            <div style="background:#0b0c0e;border-radius:6px;padding:16px;text-align:center;">
                <p style="margin:0 0 4px;color:#71717a;font-size:11px;letter-spacing:1px;text-transform:uppercase;">Need Help?</p>
                <p style="margin:0;color:#a1a1aa;font-size:13px;">Booking ID: <strong style="color:#e5b80b;">%s</strong></p>
                <p style="margin:8px 0 0;color:#71717a;font-size:12px;">Please mention your Booking ID when contacting customer support.</p>
            </div>
        </td></tr>

        <!-- Footer -->
        <tr><td style="background:#0b0c0e;padding:16px 40px;text-align:center;border-top:1px solid #2a2e37;">
            <p style="margin:0;color:#52525b;font-size:11px;">PVR Cinemas — Movie Booking System</p>
        </td></tr>

        </table>
        </td></tr>
        </table>
        </body></html>
        """;
    }
}
