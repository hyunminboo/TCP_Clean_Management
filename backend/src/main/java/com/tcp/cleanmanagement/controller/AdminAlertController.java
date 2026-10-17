package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.dto.ActionRequest;
import com.tcp.cleanmanagement.dto.AlertResponse;
import com.tcp.cleanmanagement.service.AdminAlertService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/alerts")
@RequiredArgsConstructor
public class AdminAlertController {
    private final AdminAlertService adminAlertService;

    @GetMapping
    public ResponseEntity<List<AlertResponse>> getAlerts(
            @RequestParam(required = false, defaultValue = "UNRESOLVED") String status) {
        return ResponseEntity.ok(adminAlertService.getAlerts(status));
    }

    @GetMapping("/action-logs")
    public ResponseEntity<?> getActionLogs() {
        return ResponseEntity.ok(adminAlertService.getActionLogs());
    }

    @PostMapping("/{alertId}/actions")
    public ResponseEntity<Void> resolveAlert(
            @PathVariable Long alertId,
            @RequestBody(required = false) ActionRequest request,
            HttpServletRequest httpRequest) {
        if (request == null) request = new ActionRequest();
        if (request.getAdminId() == null) {
            Object authUserId = httpRequest.getAttribute("authenticatedUserId");
            if (authUserId instanceof Number) request.setAdminId(((Number) authUserId).longValue());
        }
        adminAlertService.resolveAlert(alertId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch-actions")
    public ResponseEntity<Void> resolveAllAlerts(
            @RequestBody(required = false) ActionRequest request,
            HttpServletRequest httpRequest) {
        if (request == null) {
            request = new ActionRequest();
            request.setActionDetail("관리자 일괄 확인 처리");
        }
        if (request.getAdminId() == null) {
            Object authUserId = httpRequest.getAttribute("authenticatedUserId");
            if (authUserId instanceof Number) request.setAdminId(((Number) authUserId).longValue());
        }
        adminAlertService.resolveAllAlerts(request);
        return ResponseEntity.ok().build();
    }
}
