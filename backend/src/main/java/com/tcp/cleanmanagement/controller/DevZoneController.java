package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.dto.SensorCreateRequest;
import com.tcp.cleanmanagement.dto.ZoneCreateRequest;
import com.tcp.cleanmanagement.service.DevZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/dev", "/api/admin"})
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

    @GetMapping("/sensors")
    public ResponseEntity<?> listSensors(@RequestParam(required = false) Long zoneId) {
        return ResponseEntity.ok(devZoneService.listSensors(zoneId));
    }

    @PatchMapping("/sensors/{id}")
    public ResponseEntity<?> updateSensor(@PathVariable Long id, @RequestBody SensorCreateRequest request) {
        return ResponseEntity.ok(devZoneService.updateSensor(id, request));
    }

    @DeleteMapping("/sensors/{id}")
    public ResponseEntity<Void> deleteSensor(@PathVariable Long id) {
        devZoneService.deleteSensor(id);
        return ResponseEntity.noContent().build();
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

    @PatchMapping("/zones/{id}/worker")
    public ResponseEntity<?> assignWorker(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body) {
        Object rawId = body.get("workerId");
        Long workerId = null;
        if (rawId instanceof Number) {
            workerId = ((Number) rawId).longValue();
        } else if (rawId instanceof String && !((String) rawId).isBlank()) {
            try {
                workerId = Long.parseLong(((String) rawId).trim());
            } catch (NumberFormatException ignored) {}
        }
        String workerName = body.get("workerName") != null ? String.valueOf(body.get("workerName")) : null;
        return ResponseEntity.ok(devZoneService.assignWorkerToZone(id, workerId, workerName));
    }
}
