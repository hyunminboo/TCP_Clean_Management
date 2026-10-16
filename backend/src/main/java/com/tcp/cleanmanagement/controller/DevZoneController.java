package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.dto.SensorCreateRequest;
import com.tcp.cleanmanagement.dto.ZoneCreateRequest;
import com.tcp.cleanmanagement.service.DevZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevZoneController {
    private final DevZoneService devZoneService;

    @PostMapping("/zones")
    public ResponseEntity<Long> createZone(@RequestBody ZoneCreateRequest request) {
        return ResponseEntity.ok(devZoneService.createZone(request));
    }

    @PostMapping("/sensors")
    public ResponseEntity<Long> createSensor(@RequestBody SensorCreateRequest request) {
        return ResponseEntity.ok(devZoneService.createSensor(request));
    }

    @DeleteMapping("/zones/{id}")
    public ResponseEntity<Void> deleteZone(@PathVariable Long id) {
        devZoneService.deleteZone(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/zones/{id}/toggle-active")
    public ResponseEntity<Boolean> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(devZoneService.toggleZoneActive(id));
    }

    @PutMapping("/zones/{id}")
    public ResponseEntity<?> updateZone(@PathVariable Long id, @RequestBody ZoneCreateRequest request) {
        return ResponseEntity.ok(devZoneService.updateZone(id, request));
    }
}
