package com.moviebooking.security;

import com.moviebooking.auth.entity.User;

public interface JwtService {

    String generateAccessToken(User user);

    String generateRefreshToken(User user);

    String extractEmail(String token);

    boolean isTokenValid(String token);
}