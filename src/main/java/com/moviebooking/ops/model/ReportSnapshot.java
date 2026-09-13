package com.moviebooking.ops.model;

import com.moviebooking.auth.entity.User;
import com.moviebooking.common.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "report_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class ReportSnapshot extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30)
    private ReportType reportType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by_id", nullable = false)
    private User generatedBy;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_scope", nullable = false, length = 20)
    private ReportScope reportScope;

    @Column(name = "scope_id", nullable = false)
    private Long scopeId;

    @Column(name = "scope_name", length = 500)
    private String scopeName;

    @Column(name = "filters_json", columnDefinition = "TEXT")
    private String filtersJson;

    @Column(name = "snapshot_data", nullable = false, columnDefinition = "LONGTEXT")
    private String snapshotData;
}
