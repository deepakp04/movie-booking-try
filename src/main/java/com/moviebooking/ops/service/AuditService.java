package com.moviebooking.ops.service;

import com.moviebooking.auth.entity.User;
import com.moviebooking.ops.model.AuditAction;
import com.moviebooking.ops.model.AuditLog;
import com.moviebooking.ops.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(User user,
                    AuditAction action,
                    String targetType,
                    Long targetId,
                    Long theatreId,
                    Long showId,
                    String details,
                    String ipAddress) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUser(user);
            auditLog.setAction(action);
            auditLog.setTargetType(targetType);
            auditLog.setTargetId(targetId);
            auditLog.setTheatreId(theatreId);
            auditLog.setShowId(showId);
            auditLog.setDetails(details);
            auditLog.setIpAddress(ipAddress);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Audit logging should never break the main operation
            log.error("Failed to write audit log: action={}, targetType={}, targetId={}", action, targetType, targetId, e);
        }
    }
}
