package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.entity.SystemSetting;
import com.tcp.cleanmanagement.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class SystemSettingController {
    private final SystemSettingRepository systemSettingRepository;

    @GetMapping
    public ResponseEntity<Map<String, String>> getAllSettings() {
        Map<String, String> settings = new HashMap<>();
        // Default fallbacks if DB is empty
        settings.put("temp_warning_threshold", "28.0");
        settings.put("humidity_warning_threshold", "70.0");
        settings.put("gas_warning_threshold", "400");
        settings.put("offline_timeout_seconds", "90");
        settings.put("polling_interval_seconds", "30");
        settings.put("clean_warning_interval_hours", "4");

        for (SystemSetting setting : systemSettingRepository.findAll()) {
            settings.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return ResponseEntity.ok(settings);
    }

    @PutMapping
    public ResponseEntity<Map<String, String>> updateSettings(
            @RequestBody Map<String, String> newSettings,
            @RequestParam(required = false) Long adminId) {
        for (Map.Entry<String, String> entry : newSettings.entrySet()) {
            SystemSetting setting = systemSettingRepository.findById(entry.getKey())
                    .orElseGet(() -> SystemSetting.builder().settingKey(entry.getKey()).build());
            setting.setSettingValue(String.valueOf(entry.getValue()));
            if (adminId != null) {
                setting.setUpdatedBy(adminId);
            }
            systemSettingRepository.save(setting);
        }
        return getAllSettings();
    }
}
