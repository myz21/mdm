package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.domains.EnrollmentAuditLog;
import com.arcyintel.arcops.apple_mdm.domains.EnrollmentAuditLog.AuditAction;
import com.arcyintel.arcops.apple_mdm.domains.EnrollmentAuditLog.AuditStatus;
import com.arcyintel.arcops.apple_mdm.domains.EnrollmentAuditLog.AuditTargetType;
import com.arcyintel.arcops.apple_mdm.models.api.enrollment.EnrollmentAuditLogDto;
import com.arcyintel.arcops.apple_mdm.repositories.EnrollmentAuditLogRepository;
import com.arcyintel.arcops.apple_mdm.services.enrollment.EnrollmentAuditService;
import com.arcyintel.arcops.apple_mdm.services.mappers.EnrollmentAuditLogMapper;
import com.arcyintel.arcops.apple_mdm.utils.HttpRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentAuditServiceImpl implements EnrollmentAuditService {

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentAuditServiceImpl.class);

    private final EnrollmentAuditLogRepository auditLogRepository;
    private final EnrollmentAuditLogMapper auditLogMapper;

    @Override
    @Transactional
    public void logSuccess(AuditAction action, AuditTargetType targetType, String message, String details, HttpServletRequest request) {
        try {
            EnrollmentAuditLog log = EnrollmentAuditLog.builder()
                    .action(action)
                    .targetType(targetType)
                    .status(AuditStatus.SUCCESS)
                    .message(message)
                    .details(details)
                    .ipAddress(HttpRequestUtils.extractClientIp(request))
                    .userAgent(HttpRequestUtils.extractUserAgent(request))
                    .build();

            auditLogRepository.save(log);
            logger.info("Audit log saved: {} {} - {}", action, targetType, message);
        } catch (Exception e) {
            logger.error("Failed to save audit log: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void logFailure(AuditAction action, AuditTargetType targetType, String message, String errorMessage, HttpServletRequest request) {
        try {
            EnrollmentAuditLog log = EnrollmentAuditLog.builder()
                    .action(action)
                    .targetType(targetType)
                    .status(AuditStatus.FAILURE)
                    .message(message)
                    .errorMessage(errorMessage)
                    .ipAddress(HttpRequestUtils.extractClientIp(request))
                    .userAgent(HttpRequestUtils.extractUserAgent(request))
                    .build();

            auditLogRepository.save(log);
            logger.info("Audit log saved (failure): {} {} - {}", action, targetType, message);
        } catch (Exception e) {
            logger.error("Failed to save audit log: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnrollmentAuditLogDto> getLogs(int page, int size) {
        Page<EnrollmentAuditLog> logs = auditLogRepository.findAllByOrderByCreationDateDesc(
                PageRequest.of(page, size)
        );
        return logs.map(auditLogMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnrollmentAuditLogDto> getLogsByTargetType(AuditTargetType targetType, int page, int size) {
        Page<EnrollmentAuditLog> logs = auditLogRepository.findByTargetTypeOrderByCreationDateDesc(
                targetType,
                PageRequest.of(page, size)
        );
        return logs.map(auditLogMapper::toDto);
    }

}
