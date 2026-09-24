package com.moviebooking.security;

import com.moviebooking.common.constants.Role;
import com.moviebooking.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public Static Assets & Auth Routes
                .requestMatchers(
                    "/health",
                    "/auth/**",
                    "/auth.html", "/auth.css", "/auth.js",
                    "/catalog/**",
                    "/", "/index.html", "/css/**", "/js/**",
                    "/favicon.ico",
                    // Admin Web Assets
                    "/admin.html", "/admin.css", "/admin.js",
                    // Theatre Owner Web Assets
                    "/owner.html", "/owner.js",
                    // Razorpay webhook (called by Razorpay servers, not browser)
                    "/api/payment/webhook"
                ).permitAll()

                // Booking API - requires authentication
                .requestMatchers("/api/booking/**").authenticated()

                // ===== Scoped rules first, wildcards last =====
                // The owner analytics bar calls /api/owner/analytics/**, and it must
                // stay reachable for a THEATRE_OWNER and for an admin using the owner
                // portal. Declared explicitly (not only via the wildcard below) so the
                // intent is visible and a later reorder cannot silently drop a module.
                .requestMatchers("/api/admin/operations/**").hasRole(Role.ADMIN.name())
                .requestMatchers("/api/owner/operations/**")
                    .hasAnyRole(Role.ADMIN.name(), Role.THEATRE_OWNER.name())
                .requestMatchers("/api/owner/analytics/**")
                    .hasAnyRole(Role.ADMIN.name(), Role.THEATRE_OWNER.name())

                // Admin & Owner API Protection matching your Role Enum
                .requestMatchers("/api/admin/**").hasRole(Role.ADMIN.name())
                .requestMatchers("/api/owner/**").hasAnyRole(Role.ADMIN.name(), Role.THEATRE_OWNER.name())

                .anyRequest().authenticated()
            )
            // Rejections have to be readable. With no form-login/httpBasic entry point
            // an unauthenticated request is answered with a body-less 403, which the
            // portals can only report as "HTTP 403 while loading ..." - exactly what a
            // role failure looks like. A real 401 (and a JSON 403) lets the UI say what
            // happened and lets analytics.js refresh the token once before giving up.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    writeJsonError(response, HttpStatus.UNAUTHORIZED,
                        "Your session has expired or you are not signed in. Please sign in again."))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    writeJsonError(response, HttpStatus.FORBIDDEN,
                        "Your account role cannot access that resource."))
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Mirrors the ApiResponse/ErrorResponse envelope the portals already parse
     * ({ success, message, errors }), so a rejected request arrives as a normal
     * error message instead of an unparseable empty body.
     */
    private static void writeJsonError(HttpServletResponse response,
                                       HttpStatus status,
                                       String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
            "{\"success\":false,\"message\":\"" + message + "\",\"errors\":[]}");
    }
}
//package com.moviebooking.security;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.web.SecurityFilterChain;
//
//@Configuration
//public class SecurityConfig {
//
//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http)
//            throws Exception {
//
//        http
//                .csrf(csrf -> csrf.disable())
//                .authorizeHttpRequests(auth ->
//                        auth.anyRequest().permitAll());
//
//        return http.build();
//    }
//}

