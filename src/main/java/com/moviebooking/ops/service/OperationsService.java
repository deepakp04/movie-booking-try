package com.moviebooking.ops.service;

import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.booking.model.BookingAttendee;
import com.moviebooking.booking.model.BookingStatus;
import com.moviebooking.booking.model.SeatStatus;
import com.moviebooking.booking.model.ShowSeat;
import com.moviebooking.booking.repository.BookingAttendeeRepository;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.booking.repository.ShowSeatRepository;
import com.moviebooking.catalog.model.Show;
import com.moviebooking.catalog.model.Theatre;
import com.moviebooking.catalog.repository.ShowRepository;
import com.moviebooking.catalog.repository.TheatreRepository;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.ops.dto.OpsDTOs.*;
import com.moviebooking.payment.model.PaymentTransaction;
import com.moviebooking.payment.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class OperationsService {

    private static final Logger log = LoggerFactory.getLogger(OperationsService.class);
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH);

    private final ShowRepository showRepository;
    private final TheatreRepository theatreRepository;
    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PaymentTransactionRepository paymentRepository;
    private final UserRepository userRepository;
    private final BookingAttendeeRepository attendeeRepository;

    public OperationsService(ShowRepository showRepository,
                             TheatreRepository theatreRepository,
                             BookingRepository bookingRepository,
                             ShowSeatRepository showSeatRepository,
                             PaymentTransactionRepository paymentRepository,
                             UserRepository userRepository,
                             BookingAttendeeRepository attendeeRepository) {
        this.showRepository = showRepository;
        this.theatreRepository = theatreRepository;
        this.bookingRepository = bookingRepository;
        this.showSeatRepository = showSeatRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.attendeeRepository = attendeeRepository;
    }

    // ================= SHOW REPORT =================

    public ShowReportResponse getShowReport(Long showId, Long restrictToTheatreId) {
        Show show = showRepository.findByIdAndIsDeletedFalse(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with ID: " + showId));

        // Validate theatre scope
        validateTheatreScope(show.getScreen().getTheatre().getId(), restrictToTheatreId);

        Theatre theatre = show.getScreen().getTheatre();
        long totalSeats = showSeatRepository.countByShowId(showId);
        long confirmedTickets = showSeatRepository.countByShowIdAndStatus(showId, SeatStatus.BOOKED);

        // Booking counts
        List<com.moviebooking.booking.model.Booking> bookings = bookingRepository.findByShowId(showId);
        long confirmedBookings = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();
        long cancelledBookings = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();
        long expiredBookings = bookings.stream().filter(b -> b.getStatus() == BookingStatus.EXPIRED).count();

        // Revenue from confirmed bookings (ShowSeat.price snapshots)
        BigDecimal totalRevenue = calculateRevenueFromSeats(showId);

        // Occupancy
        BigDecimal occupancy = totalSeats > 0
                ? BigDecimal.valueOf(confirmedTickets).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalSeats), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Ticket holders for confirmed bookings
        List<TicketHolderResponse> ticketHolders = getTicketHoldersForShow(showId);

        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();

        return new ShowReportResponse(
                showId,
                show.getMovie().getTitle(),
                show.getLanguage() != null ? show.getLanguage().name() : "",
                show.getFormat() != null ? show.getFormat().getValue() : "",
                show.getMovie().getCbfcRating() != null ? show.getMovie().getCbfcRating().name() : "",
                theatre.getName(),
                theatre.getAddress(),
                theatre.getCity() != null ? theatre.getCity().getName() : "",
                show.getScreen().getName(),
                (int) totalSeats,
                show.getStartTime(),
                confirmedBookings,
                confirmedTickets,
                cancelledBookings,
                expiredBookings,
                occupancy,
                totalRevenue,
                ticketHolders,
                LocalDateTime.now().format(DISPLAY_FMT),
                currentUser
        );
    }

    // ================= THEATRE REPORT =================

    public TheatreReportResponse getTheatreReport(Long theatreId, String dateFrom, String dateTo, Long restrictToTheatreId) {
        validateTheatreScope(theatreId, restrictToTheatreId);

        Theatre theatre = theatreRepository.findByIdAndIsDeletedFalse(theatreId)
                .orElseThrow(() -> new ResourceNotFoundException("Theatre not found with ID: " + theatreId));

        LocalDateTime from = (dateFrom != null && !dateFrom.isBlank())
                ? LocalDate.parse(dateFrom).atStartOfDay()
                : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime to = (dateTo != null && !dateTo.isBlank())
                ? LocalDate.parse(dateTo).atTime(23, 59, 59)
                : LocalDateTime.now();

        // Get shows in date range for this theatre
        List<Show> shows = showRepository.findByIsDeletedFalseOrderByStartTimeDesc().stream()
                .filter(s -> s.getScreen() != null && s.getScreen().getTheatre() != null
                        && s.getScreen().getTheatre().getId().equals(theatreId))
                .filter(s -> s.getStartTime().isAfter(from) && s.getStartTime().isBefore(to))
                .toList();

        int totalScreens = (int) shows.stream().map(s -> s.getScreen().getId()).distinct().count();

        long totalSeatCapacity = 0;
        long totalConfirmedTickets = 0;
        long totalConfirmedBookings = 0;
        long totalCancelledBookings = 0;
        long totalExpiredBookings = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        List<ShowBreakdownRow> breakdown = new ArrayList<>();

        for (Show show : shows) {
            long capacity = showSeatRepository.countByShowId(show.getId());
            long confirmed = showSeatRepository.countByShowIdAndStatus(show.getId(), SeatStatus.BOOKED);

            List<com.moviebooking.booking.model.Booking> bookings = bookingRepository.findByShowId(show.getId());
            long confirmedB = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();
            long cancelledB = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();
            long expiredB = bookings.stream().filter(b -> b.getStatus() == BookingStatus.EXPIRED).count();

            BigDecimal showRevenue = calculateRevenueFromSeats(show.getId());

            totalSeatCapacity += capacity;
            totalConfirmedTickets += confirmed;
            totalConfirmedBookings += confirmedB;
            totalCancelledBookings += cancelledB;
            totalExpiredBookings += expiredB;
            totalRevenue = totalRevenue.add(showRevenue);

            BigDecimal showOccupancy = capacity > 0
                    ? BigDecimal.valueOf(confirmed).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(capacity), 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            breakdown.add(new ShowBreakdownRow(
                    show.getId(),
                    show.getMovie().getTitle(),
                    show.getScreen().getName(),
                    show.getStartTime(),
                    show.getFormat() != null ? show.getFormat().getValue() : "",
                    show.getLanguage() != null ? show.getLanguage().name() : "",
                    capacity,
                    confirmed,
                    showOccupancy,
                    showRevenue
            ));
        }

        BigDecimal occupancy = totalSeatCapacity > 0
                ? BigDecimal.valueOf(totalConfirmedTickets).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalSeatCapacity), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();

        return new TheatreReportResponse(
                theatreId,
                theatre.getName(),
                theatre.getAddress(),
                theatre.getCity() != null ? theatre.getCity().getName() : "",
                totalScreens,
                shows.size(),
                totalSeatCapacity,
                totalConfirmedTickets,
                totalConfirmedBookings,
                totalCancelledBookings,
                totalExpiredBookings,
                occupancy,
                totalRevenue,
                breakdown,
                from.format(DateTimeFormatter.ISO_LOCAL_DATE),
                to.format(DateTimeFormatter.ISO_LOCAL_DATE),
                LocalDateTime.now().format(DISPLAY_FMT),
                currentUser
        );
    }

    // ================= TICKET HOLDER REPORT =================

    public List<TicketHolderResponse> getTicketHolders(Long showId, Long restrictToTheatreId) {
        Show show = showRepository.findByIdAndIsDeletedFalse(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with ID: " + showId));

        validateTheatreScope(show.getScreen().getTheatre().getId(), restrictToTheatreId);

        return getTicketHoldersForShow(showId);
    }

    // ================= BOOKING COUNTS =================

    public BookingSummarySnapshot getBookingSummary(Long showId) {
        List<com.moviebooking.booking.model.Booking> bookings = bookingRepository.findByShowId(showId);

        long confirmedBookings = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();
        long cancelledBookings = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();
        long expiredBookings = bookings.stream().filter(b -> b.getStatus() == BookingStatus.EXPIRED).count();
        long confirmedTickets = showSeatRepository.countByShowIdAndStatus(showId, SeatStatus.BOOKED);
        BigDecimal totalRevenue = calculateRevenueFromSeats(showId);

        List<TicketHolderSnapshot> holders = getTicketHoldersForShow(showId).stream()
                .map(t -> new TicketHolderSnapshot(
                        t.bookingId(), t.transactionId(), t.customerName(), t.customerPhone(), t.customerEmail(),
                        t.attendeeName(), t.attendeePhone(), t.attendeeDob(), t.bookingForSelf(),
                        t.seatCode(), t.seatTier(), t.ticketPrice(), t.bookingTime(),
                        t.bookingStatus(), t.paymentStatus(), t.paymentTransactionId()
                ))
                .toList();

        return new BookingSummarySnapshot(
                confirmedBookings, confirmedTickets, cancelledBookings, expiredBookings,
                totalRevenue, holders
        );
    }

    // ================= HELPER METHODS =================

    private List<TicketHolderResponse> getTicketHoldersForShow(Long showId) {
        List<TicketHolderResponse> holders = new ArrayList<>();

        List<com.moviebooking.booking.model.Booking> bookings = bookingRepository.findByShowId(showId);
        List<com.moviebooking.booking.model.Booking> confirmedBookings = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .toList();

        List<ShowSeat> allSeats = showSeatRepository.findByShowId(showId);

        for (com.moviebooking.booking.model.Booking booking : confirmedBookings) {
            // Get seats for this booking
            List<ShowSeat> bookingSeats = allSeats.stream()
                    .filter(s -> booking.getId().equals(s.getBookingId()) && s.getStatus() == SeatStatus.BOOKED)
                    .toList();

            // Get payment info
            PaymentTransaction tx = paymentRepository.findByBookingId(booking.getId()).orElse(null);

            // Get booking user info (fallback)
            User user = booking.getUser();
            String bookingUserName = user != null && user.getName() != null ? user.getName() : "";
            String bookingUserPhone = user != null && user.getPhone() != null ? user.getPhone() : "";
            String bookingUserEmail = user != null && user.getEmail() != null ? user.getEmail() : "";

            // Get attendees for this booking (each seat has its own name/DOB/phone)
            List<BookingAttendee> attendees = attendeeRepository.findByBookingIdAndIsDeletedFalse(booking.getId());
            // Build a lookup: seatCode -> attendee
            java.util.Map<String, BookingAttendee> attendeeBySeat = new java.util.HashMap<>();
            for (BookingAttendee att : attendees) {
                if (att.getSeatCode() != null) {
                    attendeeBySeat.put(att.getSeatCode(), att);
                }
            }

            for (ShowSeat seat : bookingSeats) {
                BookingAttendee att = attendeeBySeat.get(seat.getSeatCode());
                holders.add(new TicketHolderResponse(
                        booking.getId(),
                        booking.getTransactionId(),
                        bookingUserName,
                        bookingUserPhone,
                        bookingUserEmail,
                        // Per-seat attendee info
                        att != null && att.getAttendeeName() != null ? att.getAttendeeName() : bookingUserName,
                        att != null && att.getPhone() != null ? att.getPhone() : bookingUserPhone,
                        att != null && att.getDateOfBirth() != null ? att.getDateOfBirth().toString() : null,
                        att != null ? att.getIsSelf() : null,
                        seat.getSeatCode(),
                        seat.getTierName() != null ? seat.getTierName() : "Standard",
                        seat.getPrice() != null ? seat.getPrice() : BigDecimal.ZERO,
                        booking.getCreatedAt(),
                        booking.getStatus().name(),
                        tx != null ? tx.getStatus().name() : "N/A",
                        tx != null ? tx.getRazorpayPaymentId() : null
                ));
            }
        }

        return holders;
    }

    private BigDecimal calculateRevenueFromSeats(Long showId) {
        List<ShowSeat> seats = showSeatRepository.findByShowId(showId);
        return seats.stream()
                .filter(s -> s.getStatus() == SeatStatus.BOOKED && s.getPrice() != null)
                .map(ShowSeat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateTheatreScope(Long actualTheatreId, Long restrictToTheatreId) {
        if (restrictToTheatreId != null && !actualTheatreId.equals(restrictToTheatreId)) {
            throw new BusinessException("Access denied: show does not belong to your theatre.");
        }
    }

    // ================= DROPDOWNS =================

    public List<TheatreDropdownItem> getAllTheatres() {
        return theatreRepository.findByIsDeletedFalseOrderByNameAsc().stream()
                .map(t -> new TheatreDropdownItem(
                        t.getId(),
                        t.getName(),
                        t.getCity() != null ? t.getCity().getName() : ""
                ))
                .toList();
    }

    public List<ShowDropdownItem> getAllShows() {
        return showRepository.findByIsDeletedFalseOrderByStartTimeDesc().stream()
                .map(this::toShowDropdown)
                .toList();
    }

    public List<ShowDropdownItem> getFilteredShows(Long theatreId, Long movieId, Long screenId,
                                                   String dateFrom, String dateTo) {
        return showRepository.findByIsDeletedFalseOrderByStartTimeDesc().stream()
                .filter(s -> theatreId == null || s.getScreen().getTheatre().getId().equals(theatreId))
                .filter(s -> movieId == null || s.getMovie().getId().equals(movieId))
                .filter(s -> screenId == null || s.getScreen().getId().equals(screenId))
                .filter(s -> {
                    if (dateFrom == null || dateFrom.isBlank()) return true;
                    LocalDateTime from = LocalDate.parse(dateFrom).atStartOfDay();
                    return !s.getStartTime().isBefore(from);
                })
                .filter(s -> {
                    if (dateTo == null || dateTo.isBlank()) return true;
                    LocalDateTime to = LocalDate.parse(dateTo).atTime(23, 59, 59);
                    return !s.getStartTime().isAfter(to);
                })
                .map(this::toShowDropdown)
                .toList();
    }

    public List<TheatreDropdownItem> getFilteredTheatres(Long cityId) {
        return theatreRepository.findByIsDeletedFalseOrderByNameAsc().stream()
                .filter(t -> cityId == null || (t.getCity() != null && t.getCity().getId().equals(cityId)))
                .map(t -> new TheatreDropdownItem(
                        t.getId(),
                        t.getName(),
                        t.getCity() != null ? t.getCity().getName() : ""
                ))
                .toList();
    }

    public List<ShowDropdownItem> getShowsByTheatre(Long theatreId) {
        return showRepository.findByIsDeletedFalseOrderByStartTimeDesc().stream()
                .filter(s -> s.getScreen().getTheatre().getId().equals(theatreId))
                .map(this::toShowDropdown)
                .toList();
    }

    private ShowDropdownItem toShowDropdown(Show s) {
        return new ShowDropdownItem(
                s.getId(),
                s.getMovie() != null ? s.getMovie().getTitle() : "Unknown",
                s.getScreen() != null ? s.getScreen().getName() : "",
                s.getScreen() != null && s.getScreen().getTheatre() != null ? s.getScreen().getTheatre().getName() : "",
                s.getScreen() != null && s.getScreen().getTheatre() != null && s.getScreen().getTheatre().getCity() != null ? s.getScreen().getTheatre().getCity().getName() : "",
                s.getStartTime(),
                s.getLanguage() != null ? s.getLanguage().name() : "",
                s.getFormat() != null ? s.getFormat().name() : "",
                s.getScreen() != null && s.getScreen().getTheatre() != null ? s.getScreen().getTheatre().getId() : null
        );
    }
}
