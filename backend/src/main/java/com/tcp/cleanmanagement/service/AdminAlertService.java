package com.tcp.cleanmanagement.service;

import com.tcp.cleanmanagement.dto.ActionRequest;
import com.tcp.cleanmanagement.dto.AlertResponse;
import com.tcp.cleanmanagement.entity.ActionLog;
import com.tcp.cleanmanagement.entity.Alert;
import com.tcp.cleanmanagement.entity.User;
import com.tcp.cleanmanagement.enums.AlertStatus;
import com.tcp.cleanmanagement.repository.ActionLogRepository;
import com.tcp.cleanmanagement.repository.AlertRepository;
import com.tcp.cleanmanagement.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAlertService {
    private final AlertRepository alertRepository;
    private final ActionLogRepository actionLogRepository;

    @Transactional(readOnly = true)
    public List<AlertResponse> getUnresolvedAlerts(AuthUser authUser) {
        return alertRepository.findByStatus(AlertStatus.UNRESOLVED).stream()
            // DEVELOPER 는 전체, 그 외 역할은 담당 구역(ZonePermission) 알림만
            .filter(alert -> authUser.canAccessZone(alert.getZone().getId()))
            .map(alert -> AlertResponse.builder()
                .alertId(alert.getId())
                .zoneId(alert.getZone().getId())
                .zoneName(alert.getZone().getName())
                .alertType(alert.getAlertType())
                .message(alert.getMessage())
                .createdAt(alert.getCreatedAt())
                .build())
            .collect(Collectors.toList());
    }

    @Transactional
    public void resolveAlert(Long alertId, ActionRequest request, AuthUser authUser) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid Alert ID"));

        if (!authUser.canAccessZone(alert.getZone().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "담당 구역의 알림이 아닙니다.");
        }
        
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());
        alertRepository.save(alert);

        // 조치자는 클라이언트가 보낸 adminId 가 아니라 JWT 로 인증된 사용자로 기록 (위조 방지)
        User actor = User.builder().id(authUser.id()).build();

        ActionLog log = ActionLog.builder()
                .alert(alert)
                .admin(actor)
                .actionDetail(request.getActionDetail())
                .build();
        actionLogRepository.save(log);
    }
}
