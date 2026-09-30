package com.moviebooking.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.booking.model.SeatStatus;
import com.moviebooking.booking.model.ShowSeat;
import com.moviebooking.booking.repository.ShowSeatRepository;
import com.moviebooking.catalog.model.AudioLanguage;
import com.moviebooking.catalog.model.CbfcRating;
import com.moviebooking.catalog.model.City;
import com.moviebooking.catalog.model.Movie;
import com.moviebooking.catalog.model.MovieFormat;
import com.moviebooking.catalog.model.Screen;
import com.moviebooking.catalog.model.SeatTier;
import com.moviebooking.catalog.model.Show;
import com.moviebooking.catalog.model.ShowTierPrice;
import com.moviebooking.catalog.model.Theatre;
import com.moviebooking.catalog.repository.CityRepository;
import com.moviebooking.catalog.repository.MovieRepository;
import com.moviebooking.catalog.repository.ScreenRepository;
import com.moviebooking.catalog.repository.SeatTierRepository;
import com.moviebooking.catalog.repository.ShowRepository;
import com.moviebooking.catalog.repository.ShowTierPriceRepository;
import com.moviebooking.catalog.repository.TheatreRepository;
import com.moviebooking.common.constants.Role;
import com.moviebooking.common.constants.UserStatus;
import com.moviebooking.common.response.ApiResponse;
import com.moviebooking.ops.dto.OpsDTOs.ReportSnapshotResponse;
import com.moviebooking.ops.dto.OpsDTOs.ShowTierMixRow;
import com.moviebooking.ops.dto.OpsDTOs.TierValueKpis;
import com.moviebooking.ops.dto.OpsDTOs.TierValueResponse;
import com.moviebooking.ops.dto.OpsDTOs.TierValueRow;
import com.moviebooking.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers the Pricing Tier Value report end to end: the per-tier revenue,
 * occupancy and share maths on hand-seeded seats, the owner-portal theatre
 * scoping, the security envelope on the new endpoint, and the save -> Excel
 * pipeline.
 *
 * Seats are seeded directly instead of through BookingService so every expected
 * number is exact and independent of the booking flow.
 *
 * The class is transactional, so the seeded rows roll back and the development
 * database is left untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TierValueReportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private TheatreRepository theatreRepository;

    @Autowired
    private ScreenRepository screenRepository;

    @Autowired
    private SeatTierRepository seatTierRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private ShowTierPriceRepository showTierPriceRepository;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    private User owner;
    private User admin;
    private User plainUser;
    private Theatre theatre;
    private Show show;
    private String today;

    @BeforeEach
    void seedTheatreShowAndSeats() {
        today = LocalDate.now().toString();

        City city = new City();
        city.setName("Tier Value Test City");
        city.setState("Tamil Nadu");
        city = cityRepository.save(city);

        owner = newUser("Tier Value Owner", "tier-value-owner@example.com", Role.THEATRE_OWNER);
        admin = newUser("Tier Value Admin", "tier-value-admin@example.com", Role.ADMIN);
        plainUser = newUser("Tier Value Customer", "tier-value-customer@example.com", Role.USER);

        theatre = new Theatre();
        theatre.setName("Tier Value Test Theatre");
        theatre.setAddress("1 Tier Street");
        theatre.setCity(city);
        theatre.setOwner(owner);
        theatre = theatreRepository.save(theatre);

        Screen screen = new Screen();
        screen.setName("Tier Value Test Screen");
        screen.setTheatre(theatre);
        screen.setTotalSeats(6);
        screen = screenRepository.save(screen);

        SeatTier general = saveTier(screen, "GENERAL", 0);
        SeatTier premium = saveTier(screen, "PREMIUM", 1);

        Movie movie = new Movie();
        movie.setTitle("Tier Value Test Movie");
        movie.setDurationMinutes(120);
        movie.setCbfcRating(CbfcRating.U);
        movie.setReleaseDate(LocalDate.now());
        movie = movieRepository.save(movie);

        show = new Show();
        show.setMovie(movie);
        show.setScreen(screen);
        show.setStartTime(LocalDate.now().atTime(18, 0));
        show.setLanguage(AudioLanguage.ENGLISH);
        show.setFormat(MovieFormat.TWO_D);
        show.setBasePrice(new BigDecimal("200.00"));
        show = showRepository.save(show);

        // Configured list price per tier for this show.
        showTierPriceRepository.save(tierPrice(show, general, "200.00"));
        showTierPriceRepository.save(tierPrice(show, premium, "400.00"));

        // 4 GENERAL seats (1 sold at 200) and 2 PREMIUM seats (1 sold at 400).
        saveSeat(show, "A1", "GENERAL", "200.00", SeatStatus.BOOKED);
        saveSeat(show, "A2", "GENERAL", "200.00", SeatStatus.AVAILABLE);
        saveSeat(show, "A3", "GENERAL", "200.00", SeatStatus.AVAILABLE);
        saveSeat(show, "A4", "GENERAL", "200.00", SeatStatus.AVAILABLE);
        saveSeat(show, "B1", "PREMIUM", "400.00", SeatStatus.BOOKED);
        saveSeat(show, "B2", "PREMIUM", "400.00", SeatStatus.AVAILABLE);
    }

    @Test
    void adminReportSplitsRevenueOccupancyAndSharePerTier() throws Exception {
        TierValueResponse report = fetchTierValue(
                "/api/admin/operations/tier-value?theatreId=" + theatre.getId()
                        + "&dateFrom=" + today + "&dateTo=" + today,
                admin);

        assertEquals(theatre.getName(), report.scopeName());
        assertEquals(1, report.totalShows());

        TierValueKpis kpis = report.kpis();
        assertMoney("600.00", kpis.totalRevenue());          // 200 GENERAL + 400 PREMIUM
        assertEquals(2L, kpis.totalTickets());
        assertMoney("300.00", kpis.avgRealisedPrice());      // 600 / 2 tickets
        assertMoney("200.00", kpis.avgBasePrice());
        assertMoney("100.00", kpis.pricingUplift());         // 300 realised - 200 base
        assertMoney("50.00", kpis.pricingUpliftPct());
        assertMoney("66.67", kpis.premiumRevenueMixPct());   // all PREMIUM revenue over 600
        assertMoney("1000.00", kpis.unsoldInventoryValue()); // 3x200 GENERAL + 1x400 PREMIUM

        assertEquals(2, report.tiers().size());
        // Sorted by revenue, so the premium tier leads.
        assertEquals("PREMIUM", report.tiers().get(0).tier());

        TierValueRow premium = tier(report, "PREMIUM");
        assertEquals(2L, premium.seatsTotal());
        assertEquals(1L, premium.seatsSold());
        assertMoney("50.00", premium.occupancyPct());
        assertMoney("400.00", premium.revenue());
        assertMoney("50.00", premium.ticketSharePct());
        assertMoney("66.67", premium.revenueSharePct());
        assertMoney("400.00", premium.avgRealisedPrice());
        assertMoney("400.00", premium.configuredPrice());
        assertMoney("2.00", premium.upliftMultiple());       // 400 / cheapest realised 200

        TierValueRow general = tier(report, "GENERAL");
        assertEquals(4L, general.seatsTotal());
        assertEquals(1L, general.seatsSold());
        assertMoney("25.00", general.occupancyPct());
        assertMoney("200.00", general.revenue());
        assertMoney("50.00", general.ticketSharePct());
        assertMoney("33.33", general.revenueSharePct());
        assertMoney("200.00", general.configuredPrice());
        assertMoney("1.00", general.upliftMultiple());       // the cheapest realised tier

        assertEquals(1, report.showMix().size());
        ShowTierMixRow mix = report.showMix().get(0);
        assertEquals(show.getId(), mix.showId());
        assertEquals(2L, mix.seatsSold());
        assertEquals(6L, mix.seatsTotal());
        assertMoney("33.33", mix.occupancyPct());
        assertMoney("600.00", mix.revenue());
        assertEquals("PREMIUM", mix.topTier());
        assertTrue(mix.premiumUnsold());
        assertMoney("400.00", mix.premiumUnsoldValue());     // 1 unsold PREMIUM seat x 400
    }

    @Test
    void ownerReportIsScopedToTheirOwnTheatre() throws Exception {
        TierValueResponse report = fetchTierValue(
                "/api/owner/operations/tier-value?dateFrom=" + today + "&dateTo=" + today,
                owner);

        // The theatre is resolved server-side; the client never sends an id.
        assertEquals(theatre.getName(), report.scopeName());
        assertEquals(1, report.totalShows());
        assertMoney("600.00", report.kpis().totalRevenue());
        assertEquals(2L, report.kpis().totalTickets());
    }

    @Test
    void tierValueReportCanBeSavedAsSnapshotAndExportedToExcel() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "reportType", "TIER_VALUE_REPORT",
                "theatreId", theatre.getId(),
                "dateFrom", today,
                "dateTo", today));

        String body = mockMvc.perform(post("/api/admin/operations/reports/generate")
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reportType").value("TIER_VALUE_REPORT"))
                .andExpect(jsonPath("$.data.reportScope").value("THEATRE"))
                .andReturn().getResponse().getContentAsString();

        ApiResponse<ReportSnapshotResponse> parsed =
                objectMapper.readValue(body, new TypeReference<ApiResponse<ReportSnapshotResponse>>() {});
        ReportSnapshotResponse snapshot = parsed.getData();

        assertEquals(theatre.getName(), snapshot.scopeName());
        assertNotNull(snapshot.snapshotData());
        assertTrue(snapshot.snapshotData().contains("PREMIUM"),
                "snapshot JSON should contain the tier breakdown");
        assertTrue(snapshot.snapshotData().contains("totalRevenue"));

        byte[] excel = mockMvc.perform(get("/api/admin/operations/reports/" + snapshot.id() + "/export")
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("PVR_Tier_Value")))
                .andReturn().getResponse().getContentAsByteArray();

        // xlsx files are ZIP containers, so they start with the PK signature.
        assertTrue(excel.length > 0, "exported workbook should not be empty");
        assertEquals('P', excel[0]);
        assertEquals('K', excel[1]);
    }

    @Test
    void anonymousRequestIsToldTheSessionExpired() throws Exception {
        mockMvc.perform(get("/api/admin/operations/tier-value"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("session has expired")));
    }

    @Test
    void nonPrivilegedRoleIsRejectedWithAnExplanatory403() throws Exception {
        mockMvc.perform(get("/api/admin/operations/tier-value")
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(plainUser)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("role")));
    }

    // ================= HELPERS =================

    private TierValueResponse fetchTierValue(String url, User user) throws Exception {
        String body = mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(body, new TypeReference<ApiResponse<TierValueResponse>>() {}).getData();
    }

    private static TierValueRow tier(TierValueResponse report, String name) {
        return report.tiers().stream()
                .filter(t -> name.equals(t.tier()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tier " + name + " missing from report"));
    }

    /** BigDecimal-safe comparison with a readable failure message. */
    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual, "expected " + expected + " but was null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    private SeatTier saveTier(Screen screen, String name, int order) {
        SeatTier tier = new SeatTier();
        tier.setScreen(screen);
        tier.setName(name);
        tier.setDisplayOrder(order);
        return seatTierRepository.save(tier);
    }

    private ShowTierPrice tierPrice(Show show, SeatTier tier, String price) {
        ShowTierPrice row = new ShowTierPrice();
        row.setShow(show);
        row.setSeatTier(tier);
        row.setPrice(new BigDecimal(price));
        return row;
    }

    private void saveSeat(Show show, String seatCode, String tier, String price, SeatStatus status) {
        ShowSeat seat = new ShowSeat();
        seat.setShow(show);
        seat.setSeatCode(seatCode);
        seat.setTierName(tier);
        seat.setPrice(new BigDecimal(price));
        seat.setStatus(status);
        showSeatRepository.save(seat);
    }

    private User newUser(String name, String email, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash("{noop}only-signed-token-is-used");
        user.setRole(role);
        user.setIsEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }
}
