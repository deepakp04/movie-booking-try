package com.moviebooking.audit;

import com.moviebooking.auth.entity.User;
import com.moviebooking.common.BaseEntity;
import com.moviebooking.common.constants.AuthEventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "auth_logs")
public class AuthLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthEventType eventType;

    @Column(length = 100)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;
}