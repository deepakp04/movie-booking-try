package com.moviebooking.voucher.service;

import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.booking.model.Booking;
import com.moviebooking.booking.model.BookingStatus;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.mail.service.VoucherEmailService;
import com.moviebooking.ops.model.AuditAction;
import com.moviebooking.ops.service.AuditService;
import com.moviebooking.voucher.dto.VoucherDTOs.*;
import com.moviebooking.voucher.model.Voucher;
import com.moviebooking.voucher.model.VoucherRedemption;
import com.moviebooking.voucher.model.VoucherStatus;
import com.moviebooking.voucher.repository.VoucherRedemptionRepository;
import com.moviebooking.voucher.repository.VoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ticket vouchers.
 *
 * A customer whose lifetime paid spend crosses the eligibility threshold earns
 * a voucher worth four free tickets. The admin sees eligible customers in the
 * Vouchers tab, handpicks (or selects all) and sends; the customer gets a code
 * bound to their account, valid for 30 days. Redeeming it on the seat-selection
 * page confirms the booking immediately — no payment gateway — and every
 * redemption emails a fresh balance.
 */
@Service
public class VoucherService {

    private static final Logger log = LoggerFactory.getLogger(VoucherService.class);

    /** Lifetime paid spend needed for the first voucher; every further tier unlocks one more. */
    public static final BigDecimal ELIGIBILITY_THRESHOLD = new BigDecimal("10000");

    public static final int FREE_TICKET_COUNT = 4;

    /** "On the 30th day until 23:59, invalid from day 31 at 00:00." */
    public static final int VALIDITY_DAYS = 30;

    private static final String CODE_PREFIX = "PVR4-";
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no O/0/I/1 confusion
    private static final int CODE_LENGTH = 8;

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private final VoucherRepository voucherRepository;
    private final VoucherRedemptionRepository redemptionRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final VoucherEmailService voucherEmailService;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public VoucherService(VoucherRepository voucherRepository,
                          VoucherRedemptionRepository redemptionRepository,
                          BookingRepository bookingRepository,
                          UserRepository userRepository,
                          VoucherEmailService voucherEmailService,
                          AuditService auditService) {
        this.voucherRepository = voucherRepository;
        this.redemptionRepository = redemptionRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.voucherEmailService = voucherEmailService;
        this.auditService = auditService;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    // ------------------------------------------------------------------
    // Customer: my voucher (marquee + seat page)
    // ------------------------------------------------------------------

    @Transactional
    public MyVoucherResponse getMyVoucherSummary() {
        User user = currentUser();
        expireLapsedVouchers();

        Optional<Voucher> latest = voucherRepository
                .findTopByUserIdAndIsDeletedFalseOrderByIssuedAtDesc(user.getId());
        BigDecimal spend = lifetimePaidSpend(user.getId());
        LocalDateTime now = LocalDateTime.now();

        if (latest.isEmpty()) {
            return new MyVoucherResponse(
                    null, null, FREE_TICKET_COUNT, 0, 0, BigDecimal.ZERO,
                    null, null, null, false,
                    spend.compareTo(ELIGIBILITY_THRESHOLD) > 0,
                    spend, ELIGIBILITY_THRESHOLD, List.of());
        }

        Voucher v = latest.get();
        List<VoucherRedemption> redemptions =
                redemptionRepository.findByVoucherIdOrderByRedeemedAtDesc(v.getId());

        BigDecimal valueRedeemed = redemptions.stream()
                .filter(r -> r.getReversedAt() == null)
                .map(r -> r.getAmountCovered() != null ? r.getAmountCovered() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long issuedCount = countVouchersForUser(user.getId());
        boolean active = v.isUsable(now);
        boolean eligibleAgain = !active
                && spend.compareTo(ELIGIBILITY_THRESHOLD.multiply(BigDecimal.valueOf(issuedCount + 1))) > 0;

        return new MyVoucherResponse(
                v.getId(),
                v.getCode(),
                v.getTotalFreeTickets(),
                v.getRemainingFreeTickets(),
                redemptions.stream().filter(r -> r.getReversedAt() == null)
                        .mapToInt(VoucherRedemption::getTicketsUsed).sum(),
                valueRedeemed,
                v.getIssuedAt(),
                v.getExpiresAt(),
                v.getStatus().name(),
                active,
                eligibleAgain,
                spend,
                ELIGIBILITY_THRESHOLD,
                redemptions.stream().map(this::toRedemptionRow).toList()
        );
    }

    /**
     * Preview validation used by the seat-selection page. Throws a
     * {@link BusinessException} with a user-facing reason when the code cannot
     * cover the selected seat count.
     */
    @Transactional
    public VoucherValidationResponse validateForBooking(String rawCode, Integer seats) {
        if (seats == null || seats < 1) {
            throw new BusinessException("Select at least one seat before applying a voucher.");
        }
        User user = currentUser();
        Voucher voucher = findUsableVoucher(rawCode, user, seats);

        return new VoucherValidationResponse(
                voucher.getCode(),
                voucher.getRemainingFreeTickets(),
                seats,
                seats,
                voucher.getRemainingFreeTickets() - seats,
                true,
                voucher.getExpiresAt(),
                "Voucher applied — " + seats + " free ticket" + (seats > 1 ? "s" : "")
                        + " will be used. " + (voucher.getRemainingFreeTickets() - seats)
                        + " free ticket" + (voucher.getRemainingFreeTickets() - seats == 1 ? "" : "s")
                        + " will remain."
        );
    }

    // ------------------------------------------------------------------
    // Booking integration
    // ------------------------------------------------------------------

    /**
     * Locks and validates the voucher for a booking that is about to be created.
     * Must be called inside the booking transaction; the PESSIMISTIC_WRITE lock
     * stops two concurrent bookings from spending the same tickets.
     */
    @Transactional
    public Voucher lockForBooking(String rawCode, User user, int seats) {
        return findUsableVoucher(rawCode, user, seats);
    }

    /** Debits the free tickets and records the redemption ledger row. */
    @Transactional
    public VoucherRedemption applyRedemption(Voucher voucher, Booking booking,
                                             int ticketsUsed, BigDecimal amountCovered) {
        LocalDateTime now = LocalDateTime.now();

        voucher.setRemainingFreeTickets(voucher.getRemainingFreeTickets() - ticketsUsed);
        voucher.setLastRedeemedAt(now);
        if (voucher.getRemainingFreeTickets() <= 0) {
            voucher.setStatus(VoucherStatus.EXHAUSTED);
        }
        voucherRepository.save(voucher);

        VoucherRedemption redemption = new VoucherRedemption();
        redemption.setVoucher(voucher);
        redemption.setUser(booking.getUser());
        redemption.setBookingId(booking.getId());
        redemption.setShowId(booking.getShow().getId());
        redemption.setMovieTitle(booking.getShow().getMovie().getTitle());
        redemption.setTheatreName(booking.getShow().getScreen().getTheatre().getName());
        redemption.setTicketsUsed(ticketsUsed);
        redemption.setAmountCovered(amountCovered);
        redemption.setRedeemedAt(now);
        VoucherRedemption saved = redemptionRepository.save(redemption);

        // Balance email after every voucher booking (async, outbox pattern).
        try {
            voucherEmailService.queueVoucherRedeemedEmail(voucher, booking, ticketsUsed);
        } catch (Exception emailEx) {
            log.error("Failed to queue voucher balance email for voucher {}: {}",
                    voucher.getCode(), emailEx.getMessage(), emailEx);
        }

        log.info("Voucher {} redeemed {} free ticket(s) on booking {} ({} remaining)",
                voucher.getCode(), ticketsUsed, booking.getId(), voucher.getRemainingFreeTickets());
        return saved;
    }

    /**
     * Gives the free tickets back when a voucher-funded booking is cancelled.
     * The redemption rows are reversed (not deleted) to keep the audit trail.
     * An expired voucher stays expired — its lapsed tickets are not resurrected.
     */
    @Transactional
    public void restoreOnCancellation(Booking booking) {
        if (booking == null || booking.getVoucher() == null) return;

        Voucher voucher = voucherRepository.findByIdForUpdate(booking.getVoucher().getId())
                .orElse(null);
        if (voucher == null) return;

        List<VoucherRedemption> activeRedemptions =
                redemptionRepository.findByBookingIdAndReversedAtIsNull(booking.getId());
        if (activeRedemptions.isEmpty()) return;

        int tickets = activeRedemptions.stream().mapToInt(VoucherRedemption::getTicketsUsed).sum();
        LocalDateTime now = LocalDateTime.now();
        activeRedemptions.forEach(r -> r.setReversedAt(now));
        redemptionRepository.saveAll(activeRedemptions);

        if (voucher.getStatus() != VoucherStatus.EXPIRED
                && now.isBefore(voucher.getExpiresAt())) {
            int restored = Math.min(voucher.getTotalFreeTickets(),
                    voucher.getRemainingFreeTickets() + tickets);
            voucher.setRemainingFreeTickets(restored);
            if (voucher.getStatus() == VoucherStatus.EXHAUSTED && restored > 0) {
                voucher.setStatus(VoucherStatus.ACTIVE);
            }
        }
        voucher.setLastRedeemedAt(now);
        voucherRepository.save(voucher);

        log.info("Booking {} cancelled — {} free ticket(s) returned to voucher {}",
                booking.getId(), tickets, voucher.getCode());
    }

    // ------------------------------------------------------------------
    // Admin: eligibility + issuance
    // ------------------------------------------------------------------

    /** Customers who have outspent their existing vouchers and have none active. */
    @Transactional
    public List<EligibleUserRow> listEligibleUsers(String search) {
        expireLapsedVouchers();

        List<Object[]> spendRows = bookingRepository.findUsersWithPaidSpendAbove(
                BookingStatus.CONFIRMED, ELIGIBILITY_THRESHOLD);

        Map<Long, List<Voucher>> vouchersByUser = allVouchers().stream()
                .collect(Collectors.groupingBy(v -> v.getUser().getId()));

        String needle = normalizeSearch(search);
        List<EligibleUserRow> rows = new ArrayList<>();

        for (Object[] row : spendRows) {
            User user = (User) row[0];
            BigDecimal spend = toBigDecimal(row[1]);
            long bookings = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            LocalDateTime lastBookingAt = (LocalDateTime) row[3];

            List<Voucher> vouchers = vouchersByUser.getOrDefault(user.getId(), List.of());
            long issuedCount = vouchers.size();
            boolean hasActive = vouchers.stream().anyMatch(v -> v.getStatus() == VoucherStatus.ACTIVE);
            // Each ₹10,000 tier earns one voucher: the (n+1)-th needs spend over n*10,000.
            boolean hasUnlockedTier = spend.compareTo(
                    ELIGIBILITY_THRESHOLD.multiply(BigDecimal.valueOf(issuedCount + 1))) > 0;

            if (hasActive || !hasUnlockedTier) continue;
            if (!needle.isEmpty() && !matchesUser(user, needle)) continue;

            Voucher latest = vouchers.stream()
                    .max(Comparator.comparing(Voucher::getIssuedAt))
                    .orElse(null);

            rows.add(new EligibleUserRow(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getPhone(),
                    spend,
                    bookings,
                    lastBookingAt,
                    issuedCount,
                    latest != null ? latest.getStatus().name() : null
            ));
        }
        return rows;
    }

    /**
     * Issues vouchers to the handpicked (or select-all) customers. Every user is
     * re-validated server-side: ineligible picks are skipped with a reason rather
     * than silently granted a voucher.
     */
    @Transactional
    public IssueVoucherResponse issueVouchers(List<Long> userIds, User admin, String ipAddress) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException("Select at least one customer to send a voucher to.");
        }

        expireLapsedVouchers();

        Set<Long> distinctIds = new LinkedHashSet<>(userIds);
        List<String> messages = new ArrayList<>();
        List<VoucherRow> issuedRows = new ArrayList<>();
        int issued = 0;
        int skipped = 0;

        for (Long userId : distinctIds) {
            User user = userRepository.findById(userId)
                    .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                    .orElse(null);
            if (user == null) {
                skipped++;
                messages.add("Customer #" + userId + " was not found — skipped.");
                continue;
            }

            long issuedCount = countVouchersForUser(user.getId());
            if (voucherRepository.existsByUserIdAndStatusAndIsDeletedFalse(user.getId(), VoucherStatus.ACTIVE)) {
                skipped++;
                messages.add(user.getName() + " already has an active voucher — skipped.");
                continue;
            }

            BigDecimal spend = lifetimePaidSpend(user.getId());
            BigDecimal requiredSpend = ELIGIBILITY_THRESHOLD.multiply(BigDecimal.valueOf(issuedCount + 1));
            if (spend.compareTo(requiredSpend) <= 0) {
                skipped++;
                messages.add(user.getName() + " has ₹" + spend.stripTrailingZeros().toPlainString()
                        + " of paid spend — ₹" + requiredSpend.stripTrailingZeros().toPlainString()
                        + " is needed for the next voucher — skipped.");
                continue;
            }

            Voucher voucher = createVoucher(user, admin != null ? admin.getId() : null);
            voucherEmailService.queueVoucherIssuedEmail(voucher, false);

            auditService.log(admin, AuditAction.ISSUE_VOUCHER, "VOUCHER", voucher.getId(),
                    null, null,
                    "Issued voucher " + voucher.getCode() + " (4 free tickets, valid 30 days) to "
                            + user.getEmail(), ipAddress);

            issuedRows.add(toVoucherRow(voucher, Map.of()));
            issued++;
        }

        log.info("Voucher issuance by admin {}: {} issued, {} skipped",
                admin != null ? admin.getEmail() : "?", issued, skipped);
        return new IssueVoucherResponse(issued, skipped, messages, issuedRows);
    }

    @Transactional
    public void resendVoucherEmail(Long voucherId, User admin, String ipAddress) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found with ID: " + voucherId));

        voucherEmailService.queueVoucherIssuedEmail(voucher, true);
        auditService.log(admin, AuditAction.RESEND_VOUCHER_EMAIL, "VOUCHER", voucher.getId(),
                null, null, "Resent voucher email " + voucher.getCode() + " to "
                        + voucher.getUser().getEmail(), ipAddress);
    }

    // ------------------------------------------------------------------
    // Admin: voucher table + stats
    // ------------------------------------------------------------------

    @Transactional
    public VoucherListResponse listVouchers(String status, String search, boolean completedOnly) {
        expireLapsedVouchers();

        List<Voucher> all = allVouchers();
        List<EligibleUserRow> eligible = listEligibleUsers(null);

        Map<Long, List<VoucherRedemption>> redemptionsByVoucher = redemptionRepository
                .findByVoucherIdInAndReversedAtIsNull(all.stream().map(Voucher::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(r -> r.getVoucher().getId()));

        Map<Long, String> issuerNames = userRepository.findAllById(
                        all.stream().map(Voucher::getIssuedByUserId).filter(java.util.Objects::nonNull).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        String needle = normalizeSearch(search);
        String statusFilter = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);

        List<VoucherRow> rows = new ArrayList<>();
        for (Voucher v : all) {
            if (completedOnly && v.getStatus() != VoucherStatus.EXHAUSTED) continue;
            if (!statusFilter.isEmpty() && !"ALL".equals(statusFilter)
                    && !v.getStatus().name().equals(statusFilter)) continue;
            if (!needle.isEmpty() && !matchesVoucher(v, needle)) continue;
            rows.add(toVoucherRow(v, redemptionsByVoucher, issuerNames));
        }

        long activeVouchers = all.stream().filter(v -> v.getStatus() == VoucherStatus.ACTIVE).count();
        long exhausted = all.stream().filter(v -> v.getStatus() == VoucherStatus.EXHAUSTED).count();
        long expired = all.stream().filter(v -> v.getStatus() == VoucherStatus.EXPIRED).count();
        long remainingTickets = all.stream()
                .filter(v -> v.getStatus() == VoucherStatus.ACTIVE)
                .mapToLong(v -> v.getRemainingFreeTickets() != null ? v.getRemainingFreeTickets() : 0)
                .sum();

        VoucherStats stats = new VoucherStats(
                eligible.size(),
                activeVouchers,
                exhausted,
                expired,
                remainingTickets,
                redemptionRepository.sumTicketsUsed(),
                exhausted,
                redemptionRepository.sumAmountCovered()
        );

        return new VoucherListResponse(stats, rows, rows.size());
    }

    // ------------------------------------------------------------------
    // Expiry sweep
    // ------------------------------------------------------------------

    /** Marks lapsed ACTIVE vouchers as EXPIRED. Called by the scheduler and lazily on read. */
    @Transactional
    public int expireLapsedVouchers() {
        List<Voucher> lapsed = voucherRepository.findByStatusAndExpiresAtBeforeAndIsDeletedFalse(
                VoucherStatus.ACTIVE, LocalDateTime.now());
        if (lapsed.isEmpty()) return 0;

        for (Voucher v : lapsed) {
            v.setStatus(VoucherStatus.EXPIRED);
        }
        voucherRepository.saveAll(lapsed);
        log.info("Expired {} lapsed voucher(s)", lapsed.size());
        return lapsed.size();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private Voucher findUsableVoucher(String rawCode, User user, int seats) {
        String code = normalizeCode(rawCode);
        if (code.isEmpty()) {
            throw new BusinessException("Enter your voucher code before applying it.");
        }

        Voucher voucher = voucherRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new BusinessException(
                        "That voucher code doesn't exist. Check the code and try again."));

        if (!voucher.getUser().getId().equals(user.getId())) {
            throw new BusinessException(
                    "This voucher is linked to a different account. A voucher code can only be used "
                            + "by the account it was issued to.");
        }

        if (voucher.getStatus() == VoucherStatus.ACTIVE
                && voucher.getExpiresAt() != null
                && !LocalDateTime.now().isBefore(voucher.getExpiresAt())) {
            voucher.setStatus(VoucherStatus.EXPIRED);
            voucherRepository.save(voucher);
        }

        if (voucher.getStatus() == VoucherStatus.EXPIRED) {
            throw new BusinessException("This voucher expired on "
                    + EXPIRY_FORMAT.format(voucher.getExpiresAt())
                    + ". Unused free tickets are no longer valid.");
        }
        if (voucher.getRemainingFreeTickets() == null || voucher.getRemainingFreeTickets() <= 0
                || voucher.getStatus() == VoucherStatus.EXHAUSTED) {
            throw new BusinessException("All " + voucher.getTotalFreeTickets()
                    + " free tickets on this voucher have already been used.");
        }
        if (seats > voucher.getRemainingFreeTickets()) {
            throw new BusinessException("Your voucher has only "
                    + voucher.getRemainingFreeTickets()
                    + " free ticket(s) left, but you selected " + seats + ". Reduce your selection to "
                    + voucher.getRemainingFreeTickets()
                    + " ticket(s) or fewer to use the voucher, or remove the voucher code to pay normally.");
        }
        return voucher;
    }

    private Voucher createVoucher(User user, Long issuedByUserId) {
        LocalDateTime now = LocalDateTime.now();

        Voucher voucher = new Voucher();
        voucher.setUser(user);
        voucher.setCode(generateUniqueCode());
        voucher.setTotalFreeTickets(FREE_TICKET_COUNT);
        voucher.setRemainingFreeTickets(FREE_TICKET_COUNT);
        voucher.setStatus(VoucherStatus.ACTIVE);
        voucher.setIssuedAt(now);
        // Valid through day 30 23:59; invalid the moment day 31 starts.
        voucher.setExpiresAt(now.toLocalDate().plusDays(VALIDITY_DAYS).atStartOfDay());
        voucher.setIssuedByUserId(issuedByUserId);

        Voucher saved = voucherRepository.save(voucher);
        log.info("Issued voucher {} to {} (valid until {})",
                saved.getCode(), user.getEmail(), saved.getExpiresAt());
        return saved;
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_PREFIX);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (voucherRepository.existsByCode(code));
        return code;
    }

    private BigDecimal lifetimePaidSpend(Long userId) {
        BigDecimal spend = bookingRepository.sumPaidSpendForUser(userId, BookingStatus.CONFIRMED);
        return spend != null ? spend : BigDecimal.ZERO;
    }

    private long countVouchersForUser(Long userId) {
        return allVouchers().stream().filter(v -> v.getUser().getId().equals(userId)).count();
    }

    private List<Voucher> allVouchers() {
        return voucherRepository.findAllByIsDeletedFalseOrderByIssuedAtDesc();
    }

    private static String normalizeCode(String rawCode) {
        return rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeSearch(String search) {
        return search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesUser(User user, String needle) {
        return contains(user.getName(), needle)
                || contains(user.getEmail(), needle)
                || contains(user.getPhone(), needle);
    }

    private static boolean matchesVoucher(Voucher voucher, String needle) {
        return contains(voucher.getCode(), needle)
                || matchesUser(voucher.getUser(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private RedemptionRow toRedemptionRow(VoucherRedemption r) {
        return new RedemptionRow(
                r.getId(),
                r.getBookingId(),
                r.getMovieTitle(),
                r.getTheatreName(),
                r.getTicketsUsed(),
                r.getAmountCovered(),
                r.getRedeemedAt(),
                r.getReversedAt() != null
        );
    }

    private VoucherRow toVoucherRow(Voucher v, Map<Long, List<VoucherRedemption>> redemptionsByVoucher) {
        return toVoucherRow(v, redemptionsByVoucher, Map.of());
    }

    private VoucherRow toVoucherRow(Voucher v,
                                    Map<Long, List<VoucherRedemption>> redemptionsByVoucher,
                                    Map<Long, String> issuerNames) {
        List<VoucherRedemption> redemptions = redemptionsByVoucher.getOrDefault(v.getId(), List.of());
        BigDecimal valueRedeemed = redemptions.stream()
                .map(r -> r.getAmountCovered() != null ? r.getAmountCovered() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int ticketsUsed = Math.max(0, v.getTotalFreeTickets() - v.getRemainingFreeTickets());

        return new VoucherRow(
                v.getId(),
                v.getUser().getId(),
                v.getUser().getName(),
                v.getUser().getEmail(),
                v.getUser().getPhone(),
                v.getCode(),
                v.getTotalFreeTickets(),
                v.getRemainingFreeTickets(),
                ticketsUsed,
                valueRedeemed,
                redemptions.size(),
                v.getStatus().name(),
                v.getIssuedAt(),
                v.getExpiresAt(),
                v.getLastRedeemedAt(),
                v.getStatus() == VoucherStatus.EXHAUSTED ? v.getLastRedeemedAt() : null,
                v.getIssuedByUserId() != null ? issuerNames.get(v.getIssuedByUserId()) : null
        );
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    /** Exposed for the booking service so it can normalise codes consistently. */
    public static String normalizeVoucherCode(String rawCode) {
        return normalizeCode(rawCode);
    }
}
