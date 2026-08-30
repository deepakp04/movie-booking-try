package com.moviebooking.booking.service;

import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.booking.dto.BookingDTOs.*;
import com.moviebooking.booking.model.Booking;
import com.moviebooking.booking.model.BookingStatus;
import com.moviebooking.booking.model.SeatStatus;
import com.moviebooking.booking.model.ShowSeat;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.booking.repository.ShowSeatRepository;
import com.moviebooking.catalog.model.ScreenSeat;
import com.moviebooking.catalog.model.SeatTier;
import com.moviebooking.catalog.model.SeatType;
import com.moviebooking.catalog.model.Show;
import com.moviebooking.catalog.repository.ScreenSeatRepository;
import com.moviebooking.catalog.repository.ShowRepository;
import com.moviebooking.catalog.service.ShowPricingService;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.stream.dto.SeatUpdateEvent;
import com.moviebooking.stream.service.SeatStreamService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private static final int MAX_SEATS_PER_BOOKING = 10;
    private static final int HOLD_MINUTES = 10;

    // Only used for screens whose layout has never been drawn in the Maintenance
    // tab. Once screen_seats exist for a screen the real grid is used instead.
    private static final int LEGACY_SEATS_PER_ROW = 10;

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ScreenSeatRepository screenSeatRepository;
    private final ShowPricingService showPricing;
    private final SeatStreamService seatStreamService;

    public BookingService(ShowRepository showRepository,
                           ShowSeatRepository showSeatRepository,
                           BookingRepository bookingRepository,
                           UserRepository userRepository,
                           ScreenSeatRepository screenSeatRepository,
                           ShowPricingService showPricing,
                           SeatStreamService seatStreamService) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.screenSeatRepository = screenSeatRepository;
        this.showPricing = showPricing;
        this.seatStreamService = seatStreamService;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private static String rowPart(String code) {
        int i = 0;
        while (i < code.length() && Character.isLetter(code.charAt(i))) i++;
        return code.substring(0, i);
    }

    private static int numberPart(String code) {
        int i = 0;
        while (i < code.length() && Character.isLetter(code.charAt(i))) i++;
        try {
            return Integer.parseInt(code.substring(i));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Materialization
    // ------------------------------------------------------------------

    // Creates the show_seats rows the first time a show's seat map is touched.
    //
    // Seats now come from the screen's real screen_seats grid, so the arrangement
    // the theatre drew is exactly what customers see. Tier name and price are
    // snapshotted onto each row here, which is what stops a later price or tier
    // edit from rewriting history.
    //
    // Safe under concurrency: if two requests race, the loser's insert violates
    // the (show_id, seat_code) unique constraint and is ignored.
    @Transactional
    public Show ensureSeatsInitialized(Long showId) {
        Show show = showRepository.findByIdAndIsDeletedFalse(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with ID: " + showId));

        if (showSeatRepository.countByShowId(showId) > 0) {
            return show;
        }

        Long screenId = show.getScreen().getId();
        List<ScreenSeat> definitions =
                screenSeatRepository.findByScreenIdAndIsDeletedFalseOrderByRowLabelAscColIndexAsc(screenId);

        List<ShowSeat> seats = definitions.isEmpty()
                ? buildLegacySeats(show)
                : buildSeatsFromGrid(show, definitions);

        if (seats.isEmpty()) {
            throw new BusinessException(
                    "This screen has no seats configured. Draw its layout in the Maintenance tab first.");
        }

        try {
            showSeatRepository.saveAll(seats);
        } catch (DataIntegrityViolationException ignored) {
            // Another request initialized the same show concurrently - fine.
        }
        return show;
    }

    private List<ShowSeat> buildSeatsFromGrid(Show show, List<ScreenSeat> definitions) {
        List<ShowSeat> seats = new ArrayList<>();
        for (ScreenSeat def : definitions) {
            // Pathways and blocked cells are part of the grid but not sellable,
            // so they never become show_seats rows.
            if (def.getSeatType() != SeatType.SEAT || def.getSeatNumber() == null) {
                continue;
            }
            SeatTier tier = def.getSeatTier();

            ShowSeat s = new ShowSeat();
            s.setShow(show);
            s.setSeatCode(def.getSeatCode());
            s.setScreenSeat(def);
            s.setStatus(SeatStatus.AVAILABLE);
            s.setTierName(tier != null ? tier.getName() : null);
            s.setPrice(showPricing.resolvePrice(show, tier));
            seats.add(s);
        }
        return seats;
    }

    // Flat fallback grid for screens with no drawn layout.
    private List<ShowSeat> buildLegacySeats(Show show) {
        int totalSeats = show.getScreen().getTotalSeats() == null ? 0 : show.getScreen().getTotalSeats();
        List<ShowSeat> seats = new ArrayList<>();
        int seatsLeft = totalSeats;
        int rowIndex = 0;

        while (seatsLeft > 0) {
            char rowLabel = (char) ('A' + rowIndex);
            int inRow = Math.min(LEGACY_SEATS_PER_ROW, seatsLeft);
            for (int i = 1; i <= inRow; i++) {
                ShowSeat s = new ShowSeat();
                s.setShow(show);
                s.setSeatCode("" + rowLabel + i);
                s.setStatus(SeatStatus.AVAILABLE);
                s.setPrice(show.getBasePrice());
                seats.add(s);
            }
            seatsLeft -= inRow;
            rowIndex++;
        }
        return seats;
    }

    // ------------------------------------------------------------------
    // Seat map
    // ------------------------------------------------------------------

    @Transactional
    public SeatMapResponse getSeatMap(Long showId) {
        Show show = ensureSeatsInitialized(showId);
        Long myUserId = currentUser().getId();
        expireStaleHolds();

        List<ShowSeat> showSeats = showSeatRepository.findByShowId(showId);
        Map<String, ShowSeat> byCode = new LinkedHashMap<>();
        for (ShowSeat s : showSeats) {
            byCode.put(s.getSeatCode(), s);
        }

        Long screenId = show.getScreen().getId();
        List<ScreenSeat> grid =
                screenSeatRepository.findByScreenIdAndIsDeletedFalseOrderByRowLabelAscColIndexAsc(screenId);

        List<SeatInfo> seatInfos = new ArrayList<>();
        int rows;
        int cols;

        if (!grid.isEmpty()) {
            // Walk the physical grid so aisles land in the right columns.
            rows = (int) grid.stream().map(ScreenSeat::getRowLabel).distinct().count();
            cols = grid.stream().mapToInt(ScreenSeat::getColIndex).max().orElse(-1) + 1;

            for (ScreenSeat def : grid) {
                SeatTier tier = def.getSeatTier();
                String code = def.getSeatCode();
                ShowSeat live = (code == null) ? null : byCode.get(code);
                boolean sellable = def.getSeatType() == SeatType.SEAT && live != null;

                seatInfos.add(new SeatInfo(
                        code,
                        def.getRowLabel(),
                        def.getColIndex(),
                        def.getSeatNumber(),
                        def.getSeatType().name(),
                        sellable ? live.getStatus().name() : null,
                        sellable && live.getStatus() == SeatStatus.HELD
                                && myUserId.equals(live.getHeldByUserId()),
                        sellable ? live.getTierName() : (tier != null ? tier.getName() : null),
                        tier != null ? tier.getColorHex() : null,
                        sellable ? live.getPrice() : null
                ));
            }
        } else {
            // Legacy flat grid: synthesize geometry from the seat codes.
            List<ShowSeat> sorted = showSeats.stream()
                    .sorted(Comparator.comparing((ShowSeat s) -> rowPart(s.getSeatCode()))
                            .thenComparingInt(s -> numberPart(s.getSeatCode())))
                    .collect(Collectors.toList());

            for (ShowSeat s : sorted) {
                seatInfos.add(new SeatInfo(
                        s.getSeatCode(),
                        rowPart(s.getSeatCode()),
                        numberPart(s.getSeatCode()) - 1,
                        numberPart(s.getSeatCode()),
                        SeatType.SEAT.name(),
                        s.getStatus().name(),
                        s.getStatus() == SeatStatus.HELD && myUserId.equals(s.getHeldByUserId()),
                        s.getTierName(),
                        null,
                        s.getPrice()
                ));
            }
            rows = (int) sorted.stream().map(s -> rowPart(s.getSeatCode())).distinct().count();
            cols = LEGACY_SEATS_PER_ROW;
        }

        // Legend built from what the seats actually cost in this show, so it can
        // never disagree with the prices being charged.
        Map<String, TierLegend> legend = new LinkedHashMap<>();
        for (ShowSeat s : showSeats) {
            if (s.getTierName() == null) continue;
            if (!legend.containsKey(s.getTierName())) {
                String color = (s.getScreenSeat() != null && s.getScreenSeat().getSeatTier() != null)
                        ? s.getScreenSeat().getSeatTier().getColorHex() : null;
                legend.put(s.getTierName(), new TierLegend(s.getTierName(), color, s.getPrice()));
            }
        }

        int available = (int) showSeats.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();

        return new SeatMapResponse(
                showId,
                show.getMovie().getTitle(),
                show.getScreen().getTheatre().getName(),
                show.getScreen().getName(),
                show.getStartTime(),
                show.getLanguage() != null ? show.getLanguage().name() : null,
                show.getFormat() != null ? show.getFormat().getValue() : null,
                show.getHasCaptions(),
                rows,
                cols,
                showSeats.size(),
                available,
                MAX_SEATS_PER_BOOKING,
                new ArrayList<>(legend.values()),
                seatInfos
        );
    }

    // ------------------------------------------------------------------
    // Holding seats
    // ------------------------------------------------------------------

    // All-or-nothing: either every requested seat is held, or none are.
    @Transactional
    public BookingResponse holdSeats(HoldSeatsRequest req) {
        if (req.seatCodes() == null || req.seatCodes().isEmpty()) {
            throw new BusinessException("Select at least one seat.");
        }
        Set<String> uniqueCodes = new LinkedHashSet<>(req.seatCodes());
        if (uniqueCodes.size() > MAX_SEATS_PER_BOOKING) {
            throw new BusinessException(
                    "A maximum of " + MAX_SEATS_PER_BOOKING + " seats can be booked at once.");
        }

        Show show = ensureSeatsInitialized(req.showId());
        if (show.getStartTime() != null && show.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("This show has already started.");
        }

        User user = currentUser();
        expireStaleHolds();

        List<String> codesList = new ArrayList<>(uniqueCodes);
        List<ShowSeat> lockedSeats = showSeatRepository.findForUpdate(req.showId(), codesList);

        if (lockedSeats.size() != codesList.size()) {
            throw new BusinessException("One or more selected seats do not exist on this screen.");
        }

        LocalDateTime now = LocalDateTime.now();
        List<String> unavailable = lockedSeats.stream()
                .filter(s -> s.getStatus() == SeatStatus.BOOKED
                        || (s.getStatus() == SeatStatus.HELD
                            && s.getHoldExpiresAt() != null
                            && s.getHoldExpiresAt().isAfter(now)
                            && !user.getId().equals(s.getHeldByUserId())))
                .map(ShowSeat::getSeatCode)
                .collect(Collectors.toList());

        if (!unavailable.isEmpty()) {
            throw new BusinessException(
                    "These seats are no longer available: " + String.join(", ", unavailable));
        }

        // Total is summed from each seat's own snapshotted price rather than
        // basePrice x count, which is what makes tiered pricing correct.
        BigDecimal total = lockedSeats.stream()
                .map(s -> s.getPrice() != null ? s.getPrice() : show.getBasePrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime expiry = now.plusMinutes(HOLD_MINUTES);

        Booking booking = new Booking();
        booking.setShow(show);
        booking.setUser(user);
        booking.setSeatCodes(String.join(",", codesList));
        booking.setNumberOfSeats(codesList.size());
        booking.setTotalAmount(total);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTransactionId(UUID.randomUUID().toString());
        booking.setHoldExpiresAt(expiry);
        Booking savedBooking = bookingRepository.save(booking);

        for (ShowSeat s : lockedSeats) {
            s.setStatus(SeatStatus.HELD);
            s.setHeldByUserId(user.getId());
            s.setHoldExpiresAt(expiry);
            s.setBookingId(savedBooking.getId());
            
            // Broadcast real-time seat update via SSE
            seatStreamService.broadcastSeatUpdate(show.getId(), 
                new SeatUpdateEvent(show.getId(), s.getSeatCode(), "HELD", user.getId(), true, "HELD", s.getPrice()));
        }
        showSeatRepository.saveAll(lockedSeats);

        return toBookingResponse(savedBooking, lockedSeats);
    }

    @Transactional
    public BookingResponse getBooking(Long bookingId) {
        User user = currentUser();
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + bookingId));

        List<String> codes = List.of(booking.getSeatCodes().split(","));
        List<ShowSeat> seats = showSeatRepository.findForUpdate(booking.getShow().getId(), codes);
        return toBookingResponse(booking, seats);
    }

    /**
     * Get all bookings for the current user - for "My Bookings" page.
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookingsForUser() {
        User user = currentUser();
        List<Booking> bookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return bookings.stream()
                .map(this::toBookingResponseWithSeats)
                .toList();
    }

    /**
     * Get all bookings for a specific theatre (Admin/Owner use).
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByTheatre(Long theatreId) {
        List<Booking> bookings = bookingRepository.findByTheatreId(theatreId);
        return bookings.stream()
                .map(this::toBookingResponseWithSeats)
                .toList();
    }

    /**
     * Get all bookings for a specific show (Admin/Owner use).
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByShow(Long showId) {
        List<Booking> bookings = bookingRepository.findByShowId(showId);
        return bookings.stream()
                .map(this::toBookingResponseWithSeats)
                .toList();
    }

    /**
     * Get all bookings across the system (Admin only).
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookings() {
        List<Booking> bookings = bookingRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc();
        return bookings.stream()
                .map(this::toBookingResponseWithSeats)
                .toList();
    }

    // User backs out before paying - release the seats immediately instead of
    // waiting for the hold to lapse.
    @Transactional
    public void cancelBooking(Long bookingId) {
        User user = currentUser();
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("Only a pending booking can be cancelled.");
        }

        List<String> codes = List.of(booking.getSeatCodes().split(","));
        List<ShowSeat> seats = showSeatRepository.findForUpdate(booking.getShow().getId(), codes);
        for (ShowSeat s : seats) {
            if (booking.getId().equals(s.getBookingId())) {
                releaseSeat(s);
                
                // Broadcast real-time seat update via SSE
                seatStreamService.broadcastSeatUpdate(booking.getShow().getId(),
                    new SeatUpdateEvent(booking.getShow().getId(), s.getSeatCode(), "AVAILABLE", null, false, "RELEASED", s.getPrice()));
            }
        }
        showSeatRepository.saveAll(seats);

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    // Flips any hold whose 10-minute window has lapsed back to AVAILABLE and
    // marks its booking EXPIRED. Called opportunistically on every seat-map and
    // hold request so availability is self-healing, in addition to the scheduled
    // sweep in BookingMaintenanceScheduler.
    @Transactional
    public void expireStaleHolds() {
        LocalDateTime now = LocalDateTime.now();
        List<ShowSeat> expired = showSeatRepository.findExpiredHoldsForUpdate(now);
        if (expired.isEmpty()) return;

        Set<Long> bookingIds = new LinkedHashSet<>();
        for (ShowSeat s : expired) {
            if (s.getBookingId() != null) bookingIds.add(s.getBookingId());
            Long showId = s.getShow().getId();
            String seatCode = s.getSeatCode();
            releaseSeat(s);
            
            // Broadcast real-time seat update via SSE for expired holds
            seatStreamService.broadcastSeatUpdate(showId,
                new SeatUpdateEvent(showId, seatCode, "AVAILABLE", null, false, "EXPIRED", s.getPrice()));
        }
        showSeatRepository.saveAll(expired);

        for (Long bId : bookingIds) {
            bookingRepository.findById(bId).ifPresent(b -> {
                if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
                    b.setStatus(BookingStatus.EXPIRED);
                    bookingRepository.save(b);
                }
            });
        }
    }

    private void releaseSeat(ShowSeat s) {
        s.setStatus(SeatStatus.AVAILABLE);
        s.setHeldByUserId(null);
        s.setHoldExpiresAt(null);
        s.setBookingId(null);
    }

    private BookingResponse toBookingResponse(Booking b, List<ShowSeat> seats) {
        Show show = b.getShow();

        List<ShowSeat> ordered = seats.stream()
                .sorted(Comparator.comparing((ShowSeat s) -> rowPart(s.getSeatCode()))
                        .thenComparingInt(s -> numberPart(s.getSeatCode())))
                .collect(Collectors.toList());

        List<String> codes = ordered.stream().map(ShowSeat::getSeatCode).collect(Collectors.toList());
        List<BookedSeatLine> lines = ordered.stream()
                .map(s -> new BookedSeatLine(s.getSeatCode(), s.getTierName(), s.getPrice()))
                .collect(Collectors.toList());

        return new BookingResponse(
                b.getId(),
                b.getTransactionId(),
                show.getId(),
                show.getMovie().getTitle(),
                show.getScreen().getTheatre().getName(),
                show.getScreen().getName(),
                show.getStartTime(),
                codes.isEmpty() ? List.of(b.getSeatCodes().split(",")) : codes,
                lines,
                b.getNumberOfSeats(),
                b.getTotalAmount(),
                b.getStatus(),
                b.getHoldExpiresAt()
        );
    }

    /**
     * Convert booking to response without fetching individual seats.
     * Used for list views where we only need summary info.
     */
    private BookingResponse toBookingResponseWithSeats(Booking b) {
        Show show = b.getShow();
        List<String> codes = List.of(b.getSeatCodes().split(","));
        
        // Create minimal seat lines without full seat details for list view
        List<BookedSeatLine> lines = codes.stream()
                .map(code -> new BookedSeatLine(code, null, null))
                .toList();

        return new BookingResponse(
                b.getId(),
                b.getTransactionId(),
                show.getId(),
                show.getMovie().getTitle(),
                show.getScreen().getTheatre().getName(),
                show.getScreen().getName(),
                show.getStartTime(),
                codes,
                lines,
                b.getNumberOfSeats(),
                b.getTotalAmount(),
                b.getStatus(),
                b.getHoldExpiresAt()
        );
    }
}
