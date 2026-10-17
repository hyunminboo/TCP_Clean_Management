package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.dto.DiscoveredDeviceResponse;
import com.tcp.cleanmanagement.enums.Role;
import com.tcp.cleanmanagement.service.DiscoveredDeviceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 관리자 전용 미등록 IoT 기기 감지 및 관리 컨트롤러.
 * /api/admin/** 경로에 위치하며, ADMIN 또는 DEVELOPER 권한을 가진 관리자만 접근할 수 있습니다.
 */
@RestController
@RequestMapping("/api/admin/discovered-devices")
@RequiredArgsConstructor
public class DiscoveredDeviceController {
    private final DiscoveredDeviceService discoveredDeviceService;

    private void requireAdminPrivilege(HttpServletRequest request) {
        Object attr = request != null ? request.getAttribute("authenticatedRole") : null;
        if (attr instanceof Role role && role == Role.CLEANER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "미등록 기기 조회는 관리자 전용 기능입니다.");
        }
    }

    /**
     * 감지된 미등록 기기 목록 조회 (관리자 전용)
     */
    @GetMapping
    public List<DiscoveredDeviceResponse> listDiscoveredDevices(HttpServletRequest request) {
        requireAdminPrivilege(request);
        return discoveredDeviceService.getDiscoveredDevices();
    }

    /**
     * 감지된 미등록 기기 목록에서 특정 MAC 항목 무시/제거 (관리자 전용)
     */
    @DeleteMapping("/{mac}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismissDiscoveredDevice(@PathVariable String mac, HttpServletRequest request) {
        requireAdminPrivilege(request);
        discoveredDeviceService.removeDevice(mac);
    }
}
