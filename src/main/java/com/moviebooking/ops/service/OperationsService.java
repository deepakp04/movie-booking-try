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
import com.moviebooking.catalog.model.City;
import com.moviebooking.catalog.model.Show;
import com.moviebooking.catalog.model.ShowTierPrice;
import com.moviebooking.catalog.model.Theatre;
import com.moviebooking.catalog.repository.CityRepository;
import com.moviebooking.catalog.repository.ShowRepository;
import com.moviebooking.catalog.repository.ShowTierPriceRepository;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private final ShowTierPriceRepository showTierPriceRepository;
    private final CityRepository cityRepository;

    public OperationsService(ShowRepository showRepository,
                             TheatreRepository theatreRepository,
                             BookingRepository bookingRepository,
                             ShowSeatRepository showSeatRepository,
                             PaymentTransactionRepository paymentRepository,
                             UserRepository userRepository,
                             BookingAttendeeRepository attendeeRepository,
                             ShowTierPriceRepository showTierPriceRepository,
                             CityRepository cityRepository) {
        this.showRepository = showRepository;
        this.theatreRepository = theatreRepository;
        this.bookingRepository = bookingRepository;
        this.showSeatRepository = showSeatRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.attendeeRepository = attendeeRepository;
        this.showTierPriceRepository = showTierPriceRepository;
        this.cityRepository = cityRepository;
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
        return getTicketHolders(showId, restrictToTheatreId, "CONFIRMED");
    }

    /**
     * Ticket holders for a show, optionally narrowed by booking status.
     *
     * @param statusFilter CONFIRMED (default), ALL, CANCELLED, EXPIRED or PENDING_PAYMENT
     */
    public List<TicketHolderResponse> getTicketHolders(Long showId, Long restrictToTheatreId, String statusFilter) {
        Show show = showRepository.findByIdAndIsDeletedFalse(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with ID: " + showId));

        validateTheatreScope(show.getScreen().getTheatre().getId(), restrictToTheatreId);

        return getTicketHoldersForShow(showId, parseStatusFilter(statusFilter));
    }

    private List<BookingStatus> parseStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank() || "ALL".equalsIgnoreCase(statusFilter)) {
            return List.of(BookingStatus.values());
        }
        try {
            return List.of(BookingStatus.valueOf(statusFilter.trim().toUpperCase(Locale.ENGLISH)));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unknown booking status filter: " + statusFilter);
        }
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

    /** Confirmed-only holders — used by show reports and report snapshots. */
    private List<TicketHolderResponse> getTicketHoldersForShow(Long showId) {
        return getTicketHoldersForShow(showId, List.of(BookingStatus.CONFIRMED));
    }

    private List<TicketHolderResponse> getTicketHoldersForShow(Long showId, List<BookingStatus> statuses) {
        List<TicketHolderResponse> holders = new ArrayList<>();

        List<com.moviebooking.booking.model.Booking> bookings = bookingRepository.findByShowId(showId).stream()
                .filter(b -> statuses.contains(b.getStatus()))
                .toList();

        List<ShowSeat> allSeats = showSeatRepository.findByShowId(showId);

        // Seat lookup by code. Cancelled / expired bookings release their show_seat
        // rows (booking_id is cleared), so their seats are resolved from the
        // booking's own seat_codes snapshot instead.
        java.util.Map<String, ShowSeat> seatByCode = new java.util.HashMap<>();
        for (ShowSeat s : allSeats) {
            if (s.getSeatCode() != null) {
                seatByCode.putIfAbsent(s.getSeatCode(), s);
            }
        }

        for (com.moviebooking.booking.model.Booking booking : bookings) {
            // Seats still held by this booking
            List<ShowSeat> bookedSeats = allSeats.stream()
                    .filter(s -> booking.getId().equals(s.getBookingId()) && s.getStatus() == SeatStatus.BOOKED)
                    .toList();

            // Which seats to report on
            List<SeatRow> reportSeats = new ArrayList<>();
            if (!bookedSeats.isEmpty()) {
                for (ShowSeat seat : bookedSeats) {
                    reportSeats.add(new SeatRow(
                            seat.getSeatCode(),
                            seat.getTierName() != null ? seat.getTierName() : "Standard",
                            seat.getPrice() != null ? seat.getPrice() : BigDecimal.ZERO));
                }
            } else if (booking.getSeatCodes() != null && !booking.getSeatCodes().isBlank()) {
                for (String raw : booking.getSeatCodes().split(",")) {
                    String code = raw.trim();
                    if (code.isEmpty()) {
                        continue;
                    }
                    ShowSeat seat = seatByCode.get(code);
                    reportSeats.add(new SeatRow(
                            code,
                            seat != null && seat.getTierName() != null ? seat.getTierName() : "Standard",
                            seat != null && seat.getPrice() != null ? seat.getPrice() : BigDecimal.ZERO));
                }
            }

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

            for (SeatRow seat : reportSeats) {
                BookingAttendee att = attendeeBySeat.get(seat.seatCode());
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
                        seat.seatCode(),
                        seat.tierName(),
                        seat.price(),
                        booking.getCreatedAt(),
                        booking.getStatus().name(),
                        // Voucher bookings have no gateway transaction — surface the
                        // voucher itself so ticket-holder reports stay meaningful.
                        tx != null ? tx.getStatus().name()
                                : (booking.isVoucherBooking() ? "VOUCHER" : "N/A"),
                        tx != null ? tx.getRazorpayPaymentId()
                                : (booking.isVoucherBooking() ? booking.getVoucherCode() : null)
                ));
            }
        }

        return holders;
    }

    /** Lightweight seat view used while assembling ticket holder rows. */
    private record SeatRow(String seatCode, String tierName, BigDecimal price) {}

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

    // ================= TIER VALUE REPORT =================

    /**
     * Business value of each pricing tier across the chosen scope: seats sold,
     * revenue and share mix, realised vs configured pricing, uplift multiple vs
     * the cheapest tier, and unsold inventory valued at list prices.
     */
    public TierValueResponse getTierValueReport(String dateFrom, String dateTo, Long cityId, Long theatreId,
                                                Long screenId, String movieTitle, Long restrictToTheatreId) {
        if (restrictToTheatreId != null) {
            if (theatreId != null && !restrictToTheatreId.equals(theatreId)) {
                throw new BusinessException("Access denied: data does not belong to your theatre.");
            }
            theatreId = restrictToTheatreId;
            cityId = null;
            screenId = null;
            movieTitle = null;
        }
        if (theatreId != null) {
            validateTheatreScope(theatreId, restrictToTheatreId);
        }

        // Effectively-final copies for the stream filters below.
        final Long scopeTheatreId = theatreId;
        final Long scopeCityId = cityId;
        final Long scopeScreenId = screenId;
        final String scopeMovieTitle = movieTitle;

        LocalDateTime from = (dateFrom != null && !dateFrom.isBlank())
                ? LocalDate.parse(dateFrom).atStartOfDay()
                : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime to = (dateTo != null && !dateTo.isBlank())
                ? LocalDate.parse(dateTo).atTime(23, 59, 59)
                : LocalDateTime.now();

        List<Show> shows = showRepository.findByIsDeletedFalseOrderByStartTimeDesc().stream()
                .filter(s -> s.getScreen() != null && s.getScreen().getTheatre() != null)
                .filter(s -> !s.getStartTime().isBefore(from) && !s.getStartTime().isAfter(to))
                .filter(s -> scopeTheatreId == null || s.getScreen().getTheatre().getId().equals(scopeTheatreId))
                .filter(s -> scopeCityId == null || (s.getScreen().getTheatre().getCity() != null
                        && s.getScreen().getTheatre().getCity().getId().equals(scopeCityId)))
                .filter(s -> scopeScreenId == null || s.getScreen().getId().equals(scopeScreenId))
                .filter(s -> scopeMovieTitle == null || scopeMovieTitle.isBlank()
                        || (s.getMovie() != null && scopeMovieTitle.equals(s.getMovie().getTitle())))
                .toList();

        Map<String, TierAccumulator> byTier = new LinkedHashMap<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalTickets = 0L;
        BigDecimal basePriceSum = BigDecimal.ZERO;
        int basePriceCount = 0;
        BigDecimal unsoldInventoryValue = BigDecimal.ZERO;
        List<ShowTierMixRow> showMix = new ArrayList<>();

        for (Show show : shows) {
            List<ShowSeat> seats = showSeatRepository.findByShowId(show.getId());
            if (seats.isEmpty()) {
                continue;
            }

            // Configured (list) price per tier for this show.
            Map<String, BigDecimal> configuredByTier = new LinkedHashMap<>();
            for (ShowTierPrice stp : showTierPriceRepository.findByShowIdAndIsDeletedFalse(show.getId())) {
                if (stp.getSeatTier() != null && stp.getSeatTier().getName() != null && stp.getPrice() != null) {
                    configuredByTier.put(normalizeTierName(stp.getSeatTier().getName()), stp.getPrice());
                }
            }

            Map<String, long[]> seatCounts = new LinkedHashMap<>();   // tier -> [total, sold]
            Map<String, BigDecimal> tierRevenue = new LinkedHashMap<>();
            Map<String, BigDecimal> effectivePrice = new LinkedHashMap<>();
            BigDecimal showRevenue = BigDecimal.ZERO;
            long showSold = 0L;

            for (ShowSeat seat : seats) {
                String tier = normalizeTierName(seat.getTierName());
                long[] counts = seatCounts.computeIfAbsent(tier, k -> new long[2]);
                counts[0]++;
                if (seat.getStatus() == SeatStatus.BOOKED) {
                    counts[1]++;
                    showSold++;
                    if (seat.getPrice() != null) {
                        showRevenue = showRevenue.add(seat.getPrice());
                        tierRevenue.merge(tier, seat.getPrice(), BigDecimal::add);
                    }
                }
            }

            for (Map.Entry<String, long[]> e : seatCounts.entrySet()) {
                String tier = e.getKey();
                long total = e.getValue()[0];
                long sold = e.getValue()[1];
                BigDecimal configured = configuredByTier.get(tier);
                BigDecimal realised = tierRevenue.get(tier);

                // Effective list price: configured -> realised avg -> show base price.
                BigDecimal effective;
                if (configured != null) {
                    effective = configured;
                } else if (sold > 0 && realised != null) {
                    effective = realised.divide(BigDecimal.valueOf(sold), 2, RoundingMode.HALF_UP);
                } else if (show.getBasePrice() != null) {
                    effective = show.getBasePrice();
                } else {
                    effective = null;
                }
                if (effective != null) {
                    effectivePrice.put(tier, effective);
                }

                long unsold = total - sold;
                if (unsold > 0 && effective != null) {
                    unsoldInventoryValue = unsoldInventoryValue.add(
                            effective.multiply(BigDecimal.valueOf(unsold)));
                }

                TierAccumulator acc = byTier.computeIfAbsent(tier, k -> new TierAccumulator());
                acc.seatsTotal += total;
                acc.seatsSold += sold;
                acc.revenue = acc.revenue.add(tierRevenue.getOrDefault(tier, BigDecimal.ZERO));
                if (configured != null) {
                    acc.configuredSum = acc.configuredSum.add(configured);
                    acc.configuredCount++;
                }
            }

            totalRevenue = totalRevenue.add(showRevenue);
            totalTickets += showSold;
            if (show.getBasePrice() != null && show.getBasePrice().compareTo(BigDecimal.ZERO) > 0) {
                basePriceSum = basePriceSum.add(show.getBasePrice());
                basePriceCount++;
            }

            // Top revenue-earning tier for this show.
            String topTier = "";
            BigDecimal topRevenue = BigDecimal.ZERO;
            long topSold = -1L;
            for (Map.Entry<String, long[]> e : seatCounts.entrySet()) {
                BigDecimal rev = tierRevenue.getOrDefault(e.getKey(), BigDecimal.ZERO);
                if (rev.compareTo(topRevenue) > 0 || (rev.compareTo(topRevenue) == 0 && e.getValue()[1] > topSold)) {
                    topTier = e.getKey();
                    topRevenue = rev;
                    topSold = e.getValue()[1];
                }
            }

            // Premium unsold for this show: tiers priced strictly above the show's cheapest tier.
            BigDecimal premiumUnsoldValue = BigDecimal.ZERO;
            BigDecimal minPrice = effectivePrice.values().stream().min(BigDecimal::compareTo).orElse(null);
            if (minPrice != null) {
                for (Map.Entry<String, long[]> e : seatCounts.entrySet()) {
                    BigDecimal price = effectivePrice.get(e.getKey());
                    if (price != null && price.compareTo(minPrice) > 0) {
                        long unsold = e.getValue()[0] - e.getValue()[1];
                        if (unsold > 0) {
                            premiumUnsoldValue = premiumUnsoldValue.add(
                                    price.multiply(BigDecimal.valueOf(unsold)));
                        }
                    }
                }
            }

            long showTotal = seatCounts.values().stream().mapToLong(c -> c[0]).sum();
            showMix.add(new ShowTierMixRow(
                    show.getId(),
                    show.getStartTime(),
                    show.getMovie() != null ? show.getMovie().getTitle() : "",
                    show.getScreen().getName(),
                    show.getScreen().getTheatre().getName(),
                    showSold,
                    showTotal,
                    pct(showSold, showTotal),
                    showRevenue,
                    topTier,
                    premiumUnsoldValue,
                    premiumUnsoldValue.compareTo(BigDecimal.ZERO) > 0
            ));
        }

        // Scope-level derived metrics.
        BigDecimal minRealised = byTier.values().stream()
                .filter(a -> a.seatsSold > 0)
                .map(a -> a.revenue.divide(BigDecimal.valueOf(a.seatsSold), 2, RoundingMode.HALF_UP))
                .min(BigDecimal::compareTo).orElse(null);

        BigDecimal minConfigured = byTier.values().stream()
                .filter(a -> a.configuredCount > 0)
                .map(a -> a.configuredSum.divide(BigDecimal.valueOf(a.configuredCount), 2, RoundingMode.HALF_UP))
                .min(BigDecimal::compareTo).orElse(null);

        BigDecimal premiumRevenue = BigDecimal.ZERO;
        if (minConfigured != null) {
            for (TierAccumulator acc : byTier.values()) {
                BigDecimal cfg = acc.configuredCount > 0
                        ? acc.configuredSum.divide(BigDecimal.valueOf(acc.configuredCount), 2, RoundingMode.HALF_UP)
                        : (acc.seatsSold > 0
                            ? acc.revenue.divide(BigDecimal.valueOf(acc.seatsSold), 2, RoundingMode.HALF_UP)
                            : null);
                if (cfg != null && cfg.compareTo(minConfigured) > 0) {
                    premiumRevenue = premiumRevenue.add(acc.revenue);
                }
            }
        }

        List<TierValueRow> rows = new ArrayList<>();
        for (Map.Entry<String, TierAccumulator> e : byTier.entrySet()) {
            TierAccumulator acc = e.getValue();
            BigDecimal avgRealised = acc.seatsSold > 0
                    ? acc.revenue.divide(BigDecimal.valueOf(acc.seatsSold), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal configured = acc.configuredCount > 0
                    ? acc.configuredSum.divide(BigDecimal.valueOf(acc.configuredCount), 2, RoundingMode.HALF_UP)
                    : avgRealised;
            BigDecimal uplift = (minRealised != null && minRealised.compareTo(BigDecimal.ZERO) > 0
                    && acc.seatsSold > 0)
                    ? avgRealised.divide(minRealised, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            rows.add(new TierValueRow(
                    e.getKey(),
                    acc.seatsTotal,
                    acc.seatsSold,
                    pct(acc.seatsSold, acc.seatsTotal),
                    acc.revenue,
                    pct(acc.seatsSold, totalTickets),
                    share(acc.revenue, totalRevenue),
                    avgRealised,
                    configured,
                    uplift
            ));
        }
        rows.sort(Comparator.comparing(TierValueRow::revenue)
                .thenComparing(TierValueRow::seatsSold).reversed());

        BigDecimal avgRealisedOverall = totalTickets > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalTickets), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal avgBase = basePriceCount > 0
                ? basePriceSum.divide(BigDecimal.valueOf(basePriceCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal upliftAbs = avgRealisedOverall.subtract(avgBase);
        BigDecimal upliftPct = avgBase.compareTo(BigDecimal.ZERO) > 0
                ? upliftAbs.multiply(BigDecimal.valueOf(100)).divide(avgBase, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal premiumMix = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? premiumRevenue.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();

        return new TierValueResponse(
                resolveTierScopeName(scopeTheatreId, scopeCityId),
                from.format(DateTimeFormatter.ISO_LOCAL_DATE),
                to.format(DateTimeFormatter.ISO_LOCAL_DATE),
                shows.size(),
                new TierValueKpis(totalRevenue, totalTickets, avgRealisedOverall, avgBase,
                        upliftAbs, upliftPct, premiumMix, unsoldInventoryValue),
                rows,
                showMix,
                LocalDateTime.now().format(DISPLAY_FMT),
                currentUser
        );
    }

    private static String normalizeTierName(String name) {
        return (name == null || name.isBlank()) ? "GENERAL" : name.trim();
    }

    private static BigDecimal pct(long part, long whole) {
        if (whole <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal share(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.compareTo(BigDecimal.ZERO) == 0
                || part == null || part.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(whole, 2, RoundingMode.HALF_UP);
    }

    private String resolveTierScopeName(Long theatreId, Long cityId) {
        if (theatreId != null) {
            return theatreRepository.findByIdAndIsDeletedFalse(theatreId)
                    .map(Theatre::getName)
                    .orElse("Theatre #" + theatreId);
        }
        if (cityId != null) {
            return cityRepository.findById(cityId)
                    .map(c -> "All theatres in " + c.getName())
                    .orElse("City #" + cityId);
        }
        return "All Theatres";
    }

    private static final class TierAccumulator {
        long seatsTotal;
        long seatsSold;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal configuredSum = BigDecimal.ZERO;
        int configuredCount;
    }

    // ================= DROPDOWNS =================

    public List<TheatreDropdownItem> getAllTheatres() {
        return theatreRepository.findByIsDeletedFalseOrderByNameAsc().stream()
                .map(this::toTheatreDropdown)
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
                .map(this::toTheatreDropdown)
                .toList();
    }

    private TheatreDropdownItem toTheatreDropdown(Theatre t) {
        return new TheatreDropdownItem(
                t.getId(),
                t.getName(),
                t.getCity() != null ? t.getCity().getName() : "",
                t.getCity() != null ? t.getCity().getId() : null
        );
    }

    public List<ShowDropdownItem> getShowsByTheatre(Long theatreId) {
        return showRepository.findByIsDeletedFalseOrderByStartTimeDesc().stream()
                .filter(s -> s.getScreen().getTheatre().getId().equals(theatreId))
                .map(this::toShowDropdown)
                .toList();
    }

    private ShowDropdownItem toShowDropdown(Show s) {
        var screen = s.getScreen();
        var theatre = screen != null ? screen.getTheatre() : null;
        var city = theatre != null ? theatre.getCity() : null;
        return new ShowDropdownItem(
                s.getId(),
                s.getMovie() != null ? s.getMovie().getTitle() : "Unknown",
                screen != null ? screen.getName() : "",
                theatre != null ? theatre.getName() : "",
                city != null ? city.getName() : "",
                s.getStartTime(),
                s.getLanguage() != null ? s.getLanguage().name() : "",
                s.getFormat() != null ? s.getFormat().name() : "",
                theatre != null ? theatre.getId() : null,
                city != null ? city.getId() : null,
                s.getMovie() != null ? s.getMovie().getId() : null,
                screen != null ? screen.getId() : null
        );
    }
}
