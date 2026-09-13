package com.moviebooking.security;

import com.moviebooking.common.constants.Role;
import com.moviebooking.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                
                // Admin & Owner API Protection matching your Role Enum
                .requestMatchers("/api/admin/**").hasRole(Role.ADMIN.name())
                .requestMatchers("/api/owner/**").hasAnyRole(Role.ADMIN.name(), Role.THEATRE_OWNER.name())

                // Operations & Compliance module (admin-scoped and owner-scoped)
                .requestMatchers("/api/admin/operations/**").hasRole(Role.ADMIN.name())
                .requestMatchers("/api/owner/operations/**").hasAnyRole(Role.ADMIN.name(), Role.THEATRE_OWNER.name())

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
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

