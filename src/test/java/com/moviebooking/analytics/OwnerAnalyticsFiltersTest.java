package com.moviebooking.analytics;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.catalog.model.City;
import com.moviebooking.catalog.model.Theatre;
import com.moviebooking.catalog.repository.CityRepository;
import com.moviebooking.catalog.repository.TheatreRepository;
import com.moviebooking.common.constants.Role;
import com.moviebooking.common.constants.UserStatus;
import com.moviebooking.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers /api/owner/analytics/filters end to end: the SQL that runs when a theatre
 * scope is applied, and the security rules around it.
 *
 * The regression this guards against is real and shipped: the cities query in
 * getFilterOptions inlined its theatre filter without a trailing space, so every
 * theatre owner got "AND t.id = 1ORDER BY c.name" -> MySQL 1064 -> HTTP 500, while
 * the unscoped (admin) path kept working and hid the bug.
 *
 * The class is transactional, so the seeded city/theatre/users roll back and the
 * development database is left untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnerAnalyticsFiltersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private TheatreRepository theatreRepository;

    private User owner;
    private User admin;
    private User plainUser;

    @BeforeEach
    void seedOwnedTheatre() {
        City city = new City();
        city.setName("Filter Options Test City");
        city.setState("Tamil Nadu");
        city = cityRepository.save(city);

        owner = newUser("Filter Owner", "filter-owner@example.com", Role.THEATRE_OWNER);

        admin = newUser("Filter Admin", "filter-admin@example.com", Role.ADMIN);

        plainUser = newUser("Filter Customer", "filter-customer@example.com", Role.USER);

        Theatre theatre = new Theatre();
        theatre.setName("Filter Options Test Theatre");
        theatre.setAddress("1 Test Street");
        theatre.setCity(city);
        theatre.setOwner(owner);
        theatreRepository.save(theatre);
    }

    @Test
    void ownerGetsFiltersForTheirOwnScope() throws Exception {
        // Exercises the owner-scoped SQL end to end: a failure here is exactly what
        // owners saw in production of this feature (500 on the cities query).
        mockMvc.perform(get("/api/owner/analytics/filters")
                    .header("Authorization", "Bearer " + jwtService.generateAccessToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.formats").isArray())
                .andExpect(jsonPath("$.data.formats.length()").value(5))
                .andExpect(jsonPath("$.data.languages").isArray())
                .andExpect(jsonPath("$.data.languages.length()").value(6));
    }

    @Test
    void adminCanUseTheOwnerAnalyticsEndpointUnscoped() throws Exception {
        mockMvc.perform(get("/api/owner/analytics/filters")
                    .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.formats.length()").value(5));
    }

    @Test
    void anonymousRequestIsToldTheSessionExpiredInsteadOfABodyLess403() throws Exception {
        mockMvc.perform(get("/api/owner/analytics/filters"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("session has expired")));
    }

    @Test
    void nonPrivilegedRoleIsRejectedWithAnExplanatory403() throws Exception {
        mockMvc.perform(get("/api/owner/analytics/filters")
                    .header("Authorization", "Bearer " + jwtService.generateAccessToken(plainUser)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("role")));
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
