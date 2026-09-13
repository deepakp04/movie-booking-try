package com.moviebooking.ops.repository;

import com.moviebooking.ops.model.ReportScope;
import com.moviebooking.ops.model.ReportSnapshot;
import com.moviebooking.ops.model.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportSnapshotRepository extends JpaRepository<ReportSnapshot, Long> {

    @Query("SELECT r FROM ReportSnapshot r WHERE r.isDeleted = false ORDER BY r.generatedAt DESC")
    List<ReportSnapshot> findAllActive();

    Optional<ReportSnapshot> findByIdAndIsDeletedFalse(Long id);

    @Query("SELECT r FROM ReportSnapshot r WHERE r.reportScope = :scope AND r.scopeId = :scopeId AND r.isDeleted = false ORDER BY r.generatedAt DESC")
    List<ReportSnapshot> findByScopeAndScopeId(
        @Param("scope") ReportScope scope,
        @Param("scopeId") Long scopeId
    );

    @Query("SELECT r FROM ReportSnapshot r WHERE r.reportType = :type AND r.isDeleted = false ORDER BY r.generatedAt DESC")
    List<ReportSnapshot> findByReportType(@Param("type") ReportType type);
}
