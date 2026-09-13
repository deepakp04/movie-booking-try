-- ============================================================
-- MIGRATION: Operations, Security & Compliance Reporting
-- Run this in MySQL against movie_booking_system database
-- ============================================================

-- 1. INCIDENTS
CREATE TABLE incidents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(30) NOT NULL,           -- FIRE, MEDICAL_EMERGENCY, SECURITY_INCIDENT, EVACUATION, TECHNICAL_FAILURE, POWER_FAILURE, OTHER
    severity VARCHAR(10) NOT NULL,       -- LOW, MEDIUM, HIGH, CRITICAL
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',  -- OPEN, UNDER_INVESTIGATION, RESOLVED, CLOSED

    theatre_id BIGINT NOT NULL,
    screen_id BIGINT NULL,
    show_id BIGINT NULL,

    description TEXT NOT NULL,
    incident_start_time DATETIME NOT NULL,
    reported_time DATETIME NOT NULL,
    closed_at DATETIME NULL,

    created_by_id BIGINT NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE,

    FOREIGN KEY (theatre_id) REFERENCES theatres(id),
    FOREIGN KEY (screen_id) REFERENCES screens(id),
    FOREIGN KEY (show_id) REFERENCES shows(id),
    FOREIGN KEY (created_by_id) REFERENCES users(id)
);

CREATE INDEX idx_incidents_theatre ON incidents(theatre_id);
CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incidents_created_by ON incidents(created_by_id);

-- 2. REPORT SNAPSHOTS (immutable once generated)
CREATE TABLE report_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_type VARCHAR(30) NOT NULL,    -- SHOW_REPORT, THEATRE_REPORT, TICKET_HOLDER_REPORT, INCIDENT_REPORT
    generated_by_id BIGINT NOT NULL,
    generated_at DATETIME NOT NULL,

    report_scope VARCHAR(20) NOT NULL,   -- SHOW, THEATRE, INCIDENT
    scope_id BIGINT NOT NULL,
    scope_name VARCHAR(500) NULL,        -- human-readable label

    filters_json TEXT NULL,              -- criteria used (dateFrom, dateTo, etc.)
    snapshot_data LONGTEXT NOT NULL,     -- full report data as JSON

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE,

    FOREIGN KEY (generated_by_id) REFERENCES users(id)
);

CREATE INDEX idx_report_snapshots_type ON report_snapshots(report_type);
CREATE INDEX idx_report_snapshots_scope ON report_snapshots(report_scope, scope_id);
CREATE INDEX idx_report_snapshots_generated_by ON report_snapshots(generated_by_id);

-- 3. AUDIT LOGS (operational/compliance access tracking)
CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,         -- VIEW_TICKET_HOLDERS, GENERATE_REPORT, EXPORT_REPORT, etc.
    target_type VARCHAR(30) NULL,        -- SHOW, THEATRE, INCIDENT, REPORT
    target_id BIGINT NULL,
    theatre_id BIGINT NULL,
    show_id BIGINT NULL,
    details TEXT NULL,
    ip_address VARCHAR(100) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_theatre ON audit_logs(theatre_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
