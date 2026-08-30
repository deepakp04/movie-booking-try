package com.moviebooking.auth.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.moviebooking.auth.entity.RefreshToken;
import com.moviebooking.auth.entity.User;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);
    
    List<RefreshToken>
    findByUserAndIsRevokedFalse(User user);
}