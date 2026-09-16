package com.moviebooking.ops.service;

import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.booking.model.Booking;
import com.moviebooking.booking.model.BookingAttendee;
import com.moviebooking.booking.model.BookingStatus;
import com.moviebooking.booking.repository.BookingAttendeeRepository;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.ops.dto.OpsDTOs.*;
import com.moviebooking.payment.model.PaymentTransaction;
import com.moviebooking.payment.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class Customer360Service {

    private static final Logger log = LoggerFactory.getLogger(Customer360Service.class);

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final BookingAttendeeRepository attendeeRepository;
    private final PaymentTransactionRepository paymentRepository;

    public Customer360Service(UserRepository userRepository,
                               BookingRepository bookingRepository,
                               BookingAttendeeRepository attendeeRepository,
                               PaymentTransactionRepository paymentRepository) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.attendeeRepository = attendeeRepository;
        this.paymentRepository = paymentRepository;
    }

    // ================= SEARCH =================

    public List<CustomerSearchResult> searchCustomers(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        String trimmed = query.trim();
        List<User> users;

        // If query is purely numeric, try Booking ID lookup first
        if (trimmed.matches("\\d+")) {
            Long bookingId = Long.parseLong(trimmed);
            Optional<User> bookingUser = userRepository.findByBookingId(bookingId);
            if (bookingUser.isPresent()) {
                users = List.of(bookingUser.get());
            } else {
                // Fall back to name/email/phone search
                users = userRepository.searchByNameEmailOrPhone(trimmed);
            }
        } else {
            users = userRepository.searchByNameEmailOrPhone(trimmed);
        }

        return users.stream()
                .filter(u -> u.getRole() != null && u.getRole().name().equals("USER"))
                .map(this::toSearchResult)
                .toList();
    }

    // ================= PROFILE =================

    public CustomerProfileResponse getCustomerProfile(Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + userId));

        List<Booking> bookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // Status counts
        long confirmed = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();
        long cancelled = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();
        long expired = bookings.stream().filter(b -> b.getStatus() == BookingStatus.EXPIRED).count();

        // Revenue
        BigDecimal totalSpent = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .map(Booking::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgValue = confirmed > 0
                ? totalSpent.divide(BigDecimal.valueOf(confirmed), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Patterns
        String favouriteTheatre = findFavouriteTheatre(bookings);
        String preferredFormat = findPreferredFormat(bookings);
        String preferredTimeSlot = findPreferredTimeSlot(bookings);

        long weekendCount = bookings.stream()
                .filter(b -> b.getShow() != null && b.getShow().getStartTime() != null)
                .filter(b -> {
                    DayOfWeek dow = b.getShow().getStartTime().getDayOfWeek();
                    return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
                }).count();
        long weekdayCount = bookings.size() - weekendCount;

        // Build booking rows with attendees
        List<CustomerBookingRow> bookingRows = bookings.stream()
                .map(this::toBookingRow)
                .toList();

        return new CustomerProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getDateOfBirth(),
                user.getCreatedAt(),
                (long) bookings.size(),
                confirmed,
                cancelled,
                expired,
                totalSpent,
                avgValue,
                favouriteTheatre,
                preferredFormat,
                preferredTimeSlot,
                weekendCount,
                weekdayCount,
                bookingRows
        );
    }

    // ================= HELPERS =================

    private CustomerSearchResult toSearchResult(User user) {
        List<Booking> bookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        long confirmed = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();
        BigDecimal totalSpent = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .map(Booking::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime lastBooking = bookings.isEmpty() ? null : bookings.get(0).getCreatedAt();

        return new CustomerSearchResult(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                (long) bookings.size(),
                confirmed,
                totalSpent,
                user.getCreatedAt(),
                lastBooking
        );
    }

    private CustomerBookingRow toBookingRow(Booking booking) {
        // Attendees
        List<BookingAttendee> attendees = attendeeRepository.findByBookingIdAndIsDeletedFalse(booking.getId());
        List<CustomerBookingAttendee> attendeeDtos = attendees.stream()
                .map(a -> new CustomerBookingAttendee(
                        a.getSeatCode(),
                        a.getAttendeeName(),
                        a.getPhone(),
                        a.getDateOfBirth() != null ? a.getDateOfBirth().toString() : null,
                        a.getIsSelf()
                ))
                .toList();

        // Payment
        PaymentTransaction tx = paymentRepository.findByBookingId(booking.getId()).orElse(null);

        // Show details
        String movieTitle = "";
        String theatreName = "";
        String screenName = "";
        LocalDateTime showStartTime = null;

        if (booking.getShow() != null) {
            if (booking.getShow().getMovie() != null) {
                movieTitle = booking.getShow().getMovie().getTitle();
            }
            showStartTime = booking.getShow().getStartTime();
            if (booking.getShow().getScreen() != null) {
                screenName = booking.getShow().getScreen().getName();
                if (booking.getShow().getScreen().getTheatre() != null) {
                    theatreName = booking.getShow().getScreen().getTheatre().getName();
                }
            }
        }

        return new CustomerBookingRow(
                booking.getId(),
                booking.getTransactionId(),
                movieTitle,
                theatreName,
                screenName,
                showStartTime,
                booking.getSeatCodes(),
                booking.getNumberOfSeats(),
                booking.getTotalAmount(),
                booking.getStatus().name(),
                tx != null ? tx.getStatus().name() : "N/A",
                tx != null ? tx.getRazorpayPaymentId() : null,
                booking.getCreatedAt(),
                attendeeDtos
        );
    }

    private String findFavouriteTheatre(List<Booking> bookings) {
        return bookings.stream()
                .filter(b -> b.getShow() != null && b.getShow().getScreen() != null
                        && b.getShow().getScreen().getTheatre() != null)
                .map(b -> b.getShow().getScreen().getTheatre().getName())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("—");
    }

    private String findPreferredFormat(List<Booking> bookings) {
        return bookings.stream()
                .filter(b -> b.getShow() != null && b.getShow().getFormat() != null)
                .map(b -> b.getShow().getFormat().getValue())
                .collect(Collectors.groupingBy(f -> f, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("—");
    }

    private String findPreferredTimeSlot(List<Booking> bookings) {
        return bookings.stream()
                .filter(b -> b.getShow() != null && b.getShow().getStartTime() != null)
                .map(b -> {
                    int hour = b.getShow().getStartTime().getHour();
                    if (hour < 12) return "Morning (before 12 PM)";
                    if (hour < 17) return "Afternoon (12-5 PM)";
                    if (hour < 21) return "Evening (5-9 PM)";
                    return "Night (after 9 PM)";
                })
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("—");
    }
}
