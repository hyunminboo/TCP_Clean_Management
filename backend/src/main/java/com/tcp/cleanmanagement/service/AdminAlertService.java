package com.tcp.cleanmanagement.service;

import com.tcp.cleanmanagement.dto.ActionRequest;
import com.tcp.cleanmanagement.dto.AlertResponse;
import com.tcp.cleanmanagement.entity.ActionLog;
import com.tcp.cleanmanagement.entity.Alert;
import com.tcp.cleanmanagement.entity.User;
import com.tcp.cleanmanagement.entity.DeviceSet;
import com.tcp.cleanmanagement.entity.DeviceSetHistory;
import com.tcp.cleanmanagement.enums.AlertStatus;
import com.tcp.cleanmanagement.enums.Role;
import com.tcp.cleanmanagement.repository.ActionLogRepository;
import com.tcp.cleanmanagement.repository.AlertRepository;
import com.tcp.cleanmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAlertService {
    private final AlertRepository alertRepository;
    private final ActionLogRepository actionLogRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AlertResponse> getUnresolvedAlerts() {
        return getAlerts("UNRESOLVED");
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts(String status) {
        List<Alert> alerts;
        if ("ALL".equalsIgnoreCase(status)) {
            alerts = alertRepository.findAllByOrderByCreatedAtDesc();
        } else if ("RESOLVED".equalsIgnoreCase(status)) {
            alerts = alertRepository.findByStatusOrderByCreatedAtDesc(AlertStatus.RESOLVED);
        } else {
            alerts = alertRepository.findByStatusOrderByCreatedAtDesc(AlertStatus.UNRESOLVED);
        }
        return alerts.stream()
                .map(this::responseFor)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActionLogs() {
        return actionLogRepository.findAllByOrderByCreatedAtDesc().stream().map(log -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", log.getId());
            m.put("alertId", log.getAlert() != null ? log.getAlert().getId() : null);
            m.put("zoneId", log.getAlert() != null && log.getAlert().getZone() != null ? log.getAlert().getZone().getId() : null);
            m.put("zoneName", log.getAlert() != null && log.getAlert().getZone() != null ? log.getAlert().getZone().getName() : null);
            m.put("adminId", log.getAdmin() != null ? log.getAdmin().getId() : null);
            m.put("adminUsername", log.getAdmin() != null ? log.getAdmin().getUsername() : null);
            m.put("actionDetail", log.getActionDetail());
            m.put("createdAt", log.getCreatedAt());
            return m;
        }).toList();
    }

    private AlertResponse responseFor(Alert alert) {
        DeviceSetHistory history = alert.getDeviceSetHistory();
        DeviceSet set = history != null ? history.getDeviceSet()
                : alert.getSensor() == null ? null : alert.getSensor().getDeviceSet();
        List<ActionLog> logs = actionLogRepository.findByAlertIdOrderByCreatedAtDesc(alert.getId());
        ActionLog latestLog = logs.isEmpty() ? null : logs.get(0);
        return AlertResponse.builder()
                .alertId(alert.getId())
                .zoneId(alert.getZone().getId())
                .zoneName(history == null ? alert.getZone().getName() : history.getZoneName())
                .sensorId(alert.getSensor() == null ? null : alert.getSensor().getId())
                .sensorModel(alert.getSensor() == null ? null : alert.getSensor().getSensorModel())
                .deviceSetId(set == null ? null : set.getId())
                .deviceSetHistoryId(history == null ? null : history.getId())
                .setCode(history != null ? history.getSetCode() : set == null ? null : set.getSetCode())
                .installationLabel(history != null ? history.getInstallationLabel()
                        : set == null ? null : set.getInstallationLabel())
                .alertType(alert.getAlertType())
                .status(alert.getStatus())
                .message(alert.getMessage())
                .createdAt(alert.getCreatedAt())
                .resolvedAt(alert.getResolvedAt())
                .actionDetail(latestLog != null ? latestLog.getActionDetail() : null)
                .resolvedBy(latestLog != null && latestLog.getAdmin() != null ? latestLog.getAdmin().getUsername() : null)
                .build();
    }

    private User resolveAdminUser(Long requestedAdminId) {
        if (requestedAdminId != null) {
            var found = userRepository.findById(requestedAdminId);
            if (found.isPresent()) return found.get();
        }
        return userRepository.findAll().stream().findFirst().orElseGet(() ->
                userRepository.save(User.builder()
                        .username("admin")
                        .password("1234")
                        .role(Role.ADMIN)
                        .build())
        );
    }

    @Transactional
    public void resolveAlert(Long alertId, ActionRequest request) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid Alert ID"));

        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());
        alertRepository.save(alert);

        User adminUser = resolveAdminUser(request != null ? request.getAdminId() : null);
        String detail = (request != null && request.getActionDetail() != null && !request.getActionDetail().isBlank())
                ? request.getActionDetail()
                : "관리자 확인 완료";

        ActionLog log = ActionLog.builder()
                .alert(alert)
                .admin(adminUser)
                .actionDetail(detail)
                .build();
        actionLogRepository.save(log);
    }

    @Transactional
    public void resolveAllAlerts(ActionRequest request) {
        List<Alert> targets;
        if (request != null && request.getAlertIds() != null && !request.getAlertIds().isEmpty()) {
            targets = alertRepository.findAllById(request.getAlertIds()).stream()
                    .filter(a -> a.getStatus() == AlertStatus.UNRESOLVED)
                    .toList();
        } else if (request != null && request.getZoneIds() != null && !request.getZoneIds().isEmpty()) {
            targets = alertRepository.findByStatusAndZoneIdIn(AlertStatus.UNRESOLVED, request.getZoneIds());
        } else {
            targets = alertRepository.findByStatus(AlertStatus.UNRESOLVED);
        }
        for (Alert a : targets) {
            resolveAlert(a.getId(), request);
        }
    }
}
