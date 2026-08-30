package com.moviebooking.auth.entity;

import com.moviebooking.common.BaseEntity;
import com.moviebooking.common.constants.Role;
import com.moviebooking.common.constants.UserStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "email")
        }
) 
public class User extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private Boolean isEmailVerified = false;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status =
            UserStatus.PENDING_VERIFICATION;
}