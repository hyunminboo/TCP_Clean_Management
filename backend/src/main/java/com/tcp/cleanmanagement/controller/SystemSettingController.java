package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.entity.SystemSetting;
import com.tcp.cleanmanagement.repository.SystemSettingRepository;
import com.tcp.cleanmanagement.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class SystemSettingController {
    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Map<String, String>> getAllSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        // Default fallbacks if DB is empty
        settings.put("temp_warning_threshold", "28.0");
        settings.put("temp_danger_threshold", "32.0");
        settings.put("humidity_warning_threshold", "70.0");
        settings.put("humidity_danger_threshold", "80.0");
        settings.put("gas_warning_threshold", "400");
        settings.put("gas_danger_threshold", "700");
        settings.put("offline_timeout_seconds", "300");
        settings.put("polling_interval_seconds", "30");
        settings.put("clean_warning_interval_hours", "4");
        settings.put("retention_days", "30");
        settings.put("device_key", "DEV-KEY-TCP-2026");

        for (SystemSetting setting : systemSettingRepository.findAll()) {
            settings.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return ResponseEntity.ok(settings);
    }

    @PutMapping
    public ResponseEntity<Map<String, String>> updateSettings(
            @RequestBody Map<String, String> newSettings,
            @RequestParam(required = false) Long adminId,
            HttpServletRequest httpRequest) {
        Long effectiveAdminId = adminId;
        if (effectiveAdminId == null) {
            Object authUserId = httpRequest.getAttribute("authenticatedUserId");
            if (authUserId instanceof Number) {
                effectiveAdminId = ((Number) authUserId).longValue();
            }
        }
        Long validUpdatedBy = (effectiveAdminId != null && userRepository.existsById(effectiveAdminId))
                ? effectiveAdminId
                : null;

        for (Map.Entry<String, String> entry : newSettings.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            SystemSetting setting = systemSettingRepository.findById(entry.getKey())
                    .orElseGet(() -> SystemSetting.builder().settingKey(entry.getKey()).build());
            setting.setSettingValue(String.valueOf(entry.getValue()));
            if (validUpdatedBy != null) {
                setting.setUpdatedBy(validUpdatedBy);
            }
            systemSettingRepository.save(setting);
        }
        return getAllSettings();
    }
}
