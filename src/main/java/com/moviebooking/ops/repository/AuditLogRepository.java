package com.moviebooking.ops.repository;

import com.moviebooking.ops.model.AuditAction;
import com.moviebooking.ops.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE a.theatreId = :theatreId ORDER BY a.createdAt DESC")
    List<AuditLog> findByTheatreId(@Param("theatreId") Long theatreId);

    @Query("SELECT a FROM AuditLog a WHERE a.user.id = :userId ORDER BY a.createdAt DESC")
    List<AuditLog> findByUserId(@Param("userId") Long userId);

    @Query("SELECT a FROM AuditLog a WHERE a.action = :action ORDER BY a.createdAt DESC")
    List<AuditLog> findByAction(@Param("action") AuditAction action);

    @Query("SELECT a FROM AuditLog a WHERE a.theatreId = :theatreId AND a.action = :action ORDER BY a.createdAt DESC")
    List<AuditLog> findByTheatreIdAndAction(
        @Param("theatreId") Long theatreId,
        @Param("action") AuditAction action
    );

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :from AND :to ORDER BY a.createdAt DESC")
    List<AuditLog> findByDateRange(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    @Query("SELECT a FROM AuditLog a WHERE a.targetType = :targetType AND a.targetId = :targetId ORDER BY a.createdAt DESC")
    List<AuditLog> findByTarget(
        @Param("targetType") String targetType,
        @Param("targetId") Long targetId
    );
}
