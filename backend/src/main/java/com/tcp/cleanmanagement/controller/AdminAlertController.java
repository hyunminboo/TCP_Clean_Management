package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.dto.ActionRequest;
import com.tcp.cleanmanagement.dto.AlertResponse;
import com.tcp.cleanmanagement.enums.Role;
import com.tcp.cleanmanagement.security.AuthUser;
import com.tcp.cleanmanagement.security.RequireRole;
import com.tcp.cleanmanagement.service.AdminAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 알림 조회/조치: 모든 역할 접근 가능하되, DEVELOPER 외에는 담당 구역 알림만 다룰 수 있다.
 */
@RestController
@RequestMapping("/api/admin/alerts")
@RequiredArgsConstructor
@RequireRole({Role.DEVELOPER, Role.ADMIN, Role.CLEANER})
public class AdminAlertController {
    private final AdminAlertService adminAlertService;

    @GetMapping
    public ResponseEntity<List<AlertResponse>> getUnresolvedAlerts(
            @RequestAttribute(AuthUser.ATTR) AuthUser authUser) {
        return ResponseEntity.ok(adminAlertService.getUnresolvedAlerts(authUser));
    }

    @PostMapping("/{alertId}/actions")
    public ResponseEntity<Void> resolveAlert(
            @PathVariable Long alertId, 
            @RequestBody ActionRequest request,
            @RequestAttribute(AuthUser.ATTR) AuthUser authUser) {
        adminAlertService.resolveAlert(alertId, request, authUser);
        return ResponseEntity.ok().build();
    }
}
