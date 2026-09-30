package com.moviebooking.mail.service;

import com.moviebooking.booking.model.Booking;
import com.moviebooking.mail.model.EmailOutbox;
import com.moviebooking.mail.model.EmailOutbox.EmailType;
import com.moviebooking.mail.repository.EmailOutboxRepository;
import com.moviebooking.voucher.model.Voucher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds the two voucher lifecycle emails and hands them to the outbox:
 * the "here is your voucher" mail the admin's Approve/Send action triggers, and
 * the balance-update mail queued every time free tickets are consumed.
 *
 * Emails are only ever queued — the scheduled outbox processor owns SMTP, so a
 * slow mail server can never block booking or admin operations.
 */
@Service
public class VoucherEmailService {

    private static final Logger log = LoggerFactory.getLogger(VoucherEmailService.class);

    private static final DateTimeFormatter DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'at' h:mm a", Locale.ENGLISH);

    private final EmailOutboxRepository outboxRepository;

    public VoucherEmailService(EmailOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    /**
     * Queues the voucher delivery email. Idempotent unless {@code force} is set
     * (the admin's "resend email" action).
     */
    @Transactional
    public void queueVoucherIssuedEmail(Voucher voucher, boolean force) {
        if (!force && outboxRepository.existsByVoucherIdAndEmailTypeAndStatusIn(
                voucher.getId(),
                EmailType.VOUCHER_ISSUED,
                List.of(EmailOutbox.EmailStatus.PENDING, EmailOutbox.EmailStatus.SENT))) {
            log.info("Voucher issued email already queued/sent for voucher {}, skipping", voucher.getId());
            return;
        }

        EmailOutbox outbox = new EmailOutbox();
        outbox.setVoucherId(voucher.getId());
        outbox.setEmailType(EmailType.VOUCHER_ISSUED);
        outbox.setRecipientEmail(voucher.getUser().getEmail());
        outbox.setSubject("PVR Cinemas — 🎟️ Your 4 Free Tickets Are Here (" + voucher.getCode() + ")");
        outbox.setHtmlBody(buildIssuedHtml(voucher));
        outbox.setStatus(EmailOutbox.EmailStatus.PENDING);
        outboxRepository.save(outbox);

        log.info("Voucher issued email queued for voucher {} → {}",
                voucher.getCode(), voucher.getUser().getEmail());
    }

    @Transactional
    public void queueVoucherRedeemedEmail(Voucher voucher, Booking booking, int ticketsUsed) {
        EmailOutbox outbox = new EmailOutbox();
        outbox.setVoucherId(voucher.getId());
        outbox.setBookingId(booking.getId());
        outbox.setEmailType(EmailType.VOUCHER_REDEEMED);
        outbox.setRecipientEmail(voucher.getUser().getEmail());
        outbox.setSubject("PVR Cinemas — Voucher used for " + booking.getShow().getMovie().getTitle()
                + " (" + voucher.getRemainingFreeTickets() + " of " + voucher.getTotalFreeTickets()
                + " free tickets left)");
        outbox.setHtmlBody(buildRedeemedHtml(voucher, booking, ticketsUsed));
        outbox.setStatus(EmailOutbox.EmailStatus.PENDING);
        outboxRepository.save(outbox);

        log.info("Voucher balance email queued for voucher {} after booking {}",
                voucher.getCode(), booking.getId());
    }

    // ------------------------------------------------------------------
    // Issued email
    // ------------------------------------------------------------------

    private String buildIssuedHtml(Voucher voucher) {
        String name = escapeHtml(voucher.getUser().getName());
        String validUntil = voucher.getExpiresAt() != null
                ? voucher.getExpiresAt().minusSeconds(1).format(DATETIME_FORMAT)
                : "N/A";
        String invalidFrom = voucher.getExpiresAt() != null
                ? voucher.getExpiresAt().format(DATETIME_FORMAT)
                : "N/A";

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background-color:#0d0f12;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
            <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0d0f12;padding:20px 0;">
            <tr><td align="center">
            <table width="600" cellpadding="0" cellspacing="0" style="background-color:#16191e;border-radius:8px;overflow:hidden;border:1px solid #2a2e37;">

            <tr><td style="background:linear-gradient(135deg,#1a1d23,#0d0f12);padding:32px 40px;text-align:center;border-bottom:2px solid #e5b80b;">
                <h1 style="margin:0;font-size:28px;color:#ffffff;letter-spacing:3px;">PVR <span style="color:#e5b80b;">CINEMAS</span></h1>
                <div style="margin-top:16px;display:inline-block;background:rgba(229,184,11,0.12);border:1px solid #e5b80b;border-radius:20px;padding:8px 24px;">
                    <span style="color:#fbe38a;font-size:14px;font-weight:600;">🎟️ YOU HAVE EARNED A VOUCHER</span>
                </div>
                <p style="margin:16px 0 0;color:#e4e4e7;font-size:15px;">Hi %s, thank you for being a loyal PVR customer!</p>
                <p style="margin:8px 0 0;color:#a1a1aa;font-size:13px;">Your lifetime spend crossed ₹10,000 — here are <strong style="color:#e5b80b;">4 free tickets</strong>, on us.</p>
            </td></tr>

            <tr><td style="padding:28px 40px 8px;text-align:center;">
                <p style="margin:0 0 8px;color:#71717a;font-size:11px;letter-spacing:2px;text-transform:uppercase;">Your Voucher Code</p>
                <div style="background:#0b0c0e;border:2px dashed #e5b80b;border-radius:8px;padding:20px;">
                    <p style="margin:0;color:#e5b80b;font-size:28px;font-weight:700;letter-spacing:4px;">%s</p>
                    <p style="margin:8px 0 0;color:#a1a1aa;font-size:13px;"><strong>4 free tickets</strong> • any movie, any seat, any price</p>
                </div>
            </td></tr>

            <tr><td style="padding:20px 40px 0;">
                <div style="background:#0b0c0e;border-radius:6px;padding:16px;">
                    <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Linked account</td>
                        <td style="padding:4px 0;color:#e4e4e7;font-size:13px;text-align:right;">%s</td></tr>
                    <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Usable until</td>
                        <td style="padding:4px 0;color:#b9f6ca;font-size:13px;text-align:right;">%s</td></tr>
                    <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Turns invalid</td>
                        <td style="padding:4px 0;color:#ff8a80;font-size:13px;text-align:right;">%s</td></tr>
                    <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Free tickets</td>
                        <td style="padding:4px 0;color:#e4e4e7;font-size:13px;text-align:right;">%d of %d available</td></tr>
                    </table>
                </div>
            </td></tr>

            <tr><td style="padding:24px 40px 0;">
                <h3 style="margin:0 0 10px;color:#71717a;font-size:11px;letter-spacing:2px;text-transform:uppercase;">How To Use It</h3>
                <ol style="margin:0;padding-left:18px;color:#a1a1aa;font-size:13px;line-height:1.9;">
                    <li>Sign in to your PVR Cinemas account on this website.</li>
                    <li>Pick any movie, city, theatre and show.</li>
                    <li>On the seat-selection page, type the code <strong style="color:#e5b80b;">%s</strong> in the "Have a voucher code?" box and click Apply.</li>
                    <li>Select up to your remaining free tickets, fill in attendee details and confirm — no payment gateway, the booking is confirmed instantly.</li>
                    <li>Your balance updates automatically after every booking.</li>
                </ol>
            </td></tr>

            <tr><td style="padding:24px 40px 0;">
                <h3 style="margin:0 0 10px;color:#71717a;font-size:11px;letter-spacing:2px;text-transform:uppercase;">Conditions</h3>
                <ul style="margin:0;padding-left:18px;color:#a1a1aa;font-size:13px;line-height:1.9;">
                    <li>This voucher is linked to <strong>%s</strong> only — it cannot be shared, transferred or used from another account.</li>
                    <li>Valid only on this PVR Cinemas application/website (movie-booking system). Not valid at ticket counters.</li>
                    <li>Worth 4 free tickets of any price range — premium and recliner seats included.</li>
                    <li>Tickets must be used before the validity window ends; anything unused simply lapses.</li>
                    <li>Cannot be exchanged for cash or credit, and cannot be combined with another voucher.</li>
                    <li>If you cancel a voucher booking, the free tickets are returned to your balance (while the voucher is still valid).</li>
                </ul>
            </td></tr>

            <tr><td style="padding:24px 40px 32px;">
                <div style="background:#0b0c0e;border-radius:6px;padding:16px;text-align:center;">
                    <p style="margin:0;color:#a1a1aa;font-size:13px;">See your remaining free tickets any time from the marquee on the home page.</p>
                    <p style="margin:8px 0 0;color:#71717a;font-size:12px;">Questions? Reply to this email or contact PVR customer support.</p>
                </div>
            </td></tr>

            <tr><td style="background:#0b0c0e;padding:16px 40px;text-align:center;border-top:1px solid #2a2e37;">
                <p style="margin:0;color:#52525b;font-size:11px;">PVR Cinemas — Movie Booking System</p>
            </td></tr>

            </table>
            </td></tr>
            </table>
            </body></html>
            """.formatted(
                name,
                voucher.getCode(),
                escapeHtml(voucher.getUser().getEmail()),
                validUntil,
                invalidFrom,
                voucher.getRemainingFreeTickets(), voucher.getTotalFreeTickets(),
                voucher.getCode(),
                escapeHtml(voucher.getUser().getEmail())
        );
    }

    // ------------------------------------------------------------------
    // Balance-update email (sent after every voucher booking)
    // ------------------------------------------------------------------

    private String buildRedeemedHtml(Voucher voucher, Booking booking, int ticketsUsed) {
        String name = escapeHtml(voucher.getUser().getName());
        String movie = escapeHtml(booking.getShow().getMovie().getTitle());
        String theatre = escapeHtml(booking.getShow().getScreen().getTheatre().getName());
        String screen = escapeHtml(booking.getShow().getScreen().getName());
        String seats = escapeHtml(booking.getSeatCodes());
        LocalDateTime start = booking.getShow().getStartTime();
        String showTime = start != null ? start.format(DATETIME_FORMAT) : "N/A";
        int remaining = voucher.getRemainingFreeTickets();
        boolean finished = remaining <= 0;
        String validUntil = voucher.getExpiresAt() != null
                ? voucher.getExpiresAt().minusSeconds(1).format(DATETIME_FORMAT)
                : "N/A";

        String balanceLine = finished
                ? "You have now used all " + voucher.getTotalFreeTickets() + " free tickets on this voucher. We hope you enjoyed the show!"
                : "You still have <strong style=\"color:#e5b80b;\">" + remaining + " of "
                    + voucher.getTotalFreeTickets() + "</strong> free tickets left. Use them before "
                    + validUntil + ".";

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background-color:#0d0f12;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
            <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0d0f12;padding:20px 0;">
            <tr><td align="center">
            <table width="600" cellpadding="0" cellspacing="0" style="background-color:#16191e;border-radius:8px;overflow:hidden;border:1px solid #2a2e37;">

            <tr><td style="background:linear-gradient(135deg,#1a1d23,#0d0f12);padding:32px 40px;text-align:center;border-bottom:2px solid #e5b80b;">
                <h1 style="margin:0;font-size:28px;color:#ffffff;letter-spacing:3px;">PVR <span style="color:#e5b80b;">CINEMAS</span></h1>
                <div style="margin-top:16px;display:inline-block;background:rgba(46,125,50,0.15);border:1px solid #2e7d32;border-radius:20px;padding:8px 24px;">
                    <span style="color:#b9f6ca;font-size:14px;font-weight:600;">✓ VOUCHER BOOKING CONFIRMED</span>
                </div>
                <p style="margin:16px 0 0;color:#e4e4e7;font-size:15px;">Hi %s, your free tickets have been applied.</p>
            </td></tr>

            <tr><td style="padding:28px 40px 0;">
                <div style="background:#0b0c0e;border-radius:6px;padding:16px;">
                    <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Movie</td>
                        <td style="padding:4px 0;color:#e4e4e7;font-size:13px;text-align:right;">%s</td></tr>
                    <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Theatre</td>
                        <td style="padding:4px 0;color:#e4e4e7;font-size:13px;text-align:right;">%s (%s)</td></tr>
                    <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Show time</td>
                        <td style="padding:4px 0;color:#e4e4e7;font-size:13px;text-align:right;">%s</td></tr>
                    <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Seats</td>
                        <td style="padding:4px 0;color:#e4e4e7;font-size:13px;text-align:right;">%s</td></tr>
                    <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Free tickets used</td>
                        <td style="padding:4px 0;color:#e4e4e7;font-size:13px;text-align:right;">%d (%d remaining)</td></tr>
                    <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Paid</td>
                        <td style="padding:4px 0;color:#b9f6ca;font-size:13px;text-align:right;">₹0 — paid by voucher</td></tr>
                    <tr><td style="padding:4px 0;color:#a1a1aa;font-size:13px;">Voucher code</td>
                        <td style="padding:4px 0;color:#e5b80b;font-size:13px;text-align:right;">%s</td></tr>
                    </table>
                </div>
            </td></tr>

            <tr><td style="padding:20px 40px 32px;">
                <div style="background:#0b0c0e;border-radius:6px;padding:16px;text-align:center;">
                    <p style="margin:0;color:#e4e4e7;font-size:14px;">%s</p>
                </div>
            </td></tr>

            <tr><td style="background:#0b0c0e;padding:16px 40px;text-align:center;border-top:1px solid #2a2e37;">
                <p style="margin:0;color:#52525b;font-size:11px;">PVR Cinemas — Movie Booking System</p>
            </td></tr>

            </table>
            </td></tr>
            </table>
            </body></html>
            """.formatted(
                name, movie, theatre, screen, showTime, seats, ticketsUsed, remaining, voucher.getCode(), balanceLine
        );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
