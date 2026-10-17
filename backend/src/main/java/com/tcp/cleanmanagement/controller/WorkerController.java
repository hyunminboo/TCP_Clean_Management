package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.dto.WorkerRequest;
import com.tcp.cleanmanagement.entity.Worker;
import com.tcp.cleanmanagement.entity.Zone;
import com.tcp.cleanmanagement.repository.WorkerRepository;
import com.tcp.cleanmanagement.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/admin/workers")
@RequiredArgsConstructor
public class WorkerController {
    private final WorkerRepository workerRepository;
    private final ZoneRepository zoneRepository;

    @GetMapping
    public ResponseEntity<List<Worker>> list(@RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(workerRepository.findByStatus(status));
        }
        return ResponseEntity.ok(workerRepository.findAll());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Worker> create(@RequestBody WorkerRequest request) {
        String name = request.getName() != null ? request.getName().trim() : "";
        String phone = request.getPhone() != null ? request.getPhone().trim() : "";
        Worker worker = workerRepository.findFirstByNameAndPhoneOrderByIdAsc(name, phone)
                .map(existing -> {
                    if (request.getAssignedZoneId() != null) existing.setAssignedZoneId(request.getAssignedZoneId());
                    if (request.getStatus() != null && !request.getStatus().isBlank()) existing.setStatus(request.getStatus());
                    if (request.getRole() != null && !request.getRole().isBlank()) existing.setRole(request.getRole());
                    return existing;
                })
                .orElseGet(() -> Worker.builder()
                        .name(name)
                        .phone(phone)
                        .assignedZoneId(request.getAssignedZoneId())
                        .status(request.getStatus() != null ? request.getStatus() : "AVAILABLE")
                        .role(request.getRole() != null ? request.getRole() : "CLEANER")
                        .build());
        Worker saved = workerRepository.save(worker);

        if (saved.getAssignedZoneId() != null) {
            zoneRepository.findById(saved.getAssignedZoneId()).ifPresent(zone -> {
                zone.setAssignedWorkerId(saved.getId());
                zone.setAssignedWorkerName(saved.getName());
                zoneRepository.save(zone);
            });
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Worker> update(
            @PathVariable Long id,
            @RequestBody WorkerRequest request) {
        return workerRepository.findById(id).map(worker -> {
            Long prevZoneId = worker.getAssignedZoneId();
            if (request.getName() != null && !request.getName().isBlank()) {
                worker.setName(request.getName().trim());
            }
            if (request.getPhone() != null) {
                worker.setPhone(request.getPhone().trim());
            }
            if (request.getRole() != null && !request.getRole().isBlank()) {
                worker.setRole(request.getRole().trim());
            }
            if (request.getStatus() != null && !request.getStatus().isBlank()) {
                worker.setStatus(request.getStatus().trim());
            }
            worker.setAssignedZoneId(request.getAssignedZoneId());
            Worker saved = workerRepository.save(worker);

            // 이전 구역에서 담당자 해제 처리
            if (prevZoneId != null && !Objects.equals(prevZoneId, saved.getAssignedZoneId())) {
                zoneRepository.findById(prevZoneId).ifPresent(oldZone -> {
                    if (Objects.equals(oldZone.getAssignedWorkerId(), saved.getId())) {
                        oldZone.setAssignedWorkerId(null);
                        oldZone.setAssignedWorkerName(null);
                        zoneRepository.save(oldZone);
                    }
                });
            }

            // 신규 배정 구역에 담당자 반영
            if (saved.getAssignedZoneId() != null) {
                zoneRepository.findById(saved.getAssignedZoneId()).ifPresent(newZone -> {
                    newZone.setAssignedWorkerId(saved.getId());
                    newZone.setAssignedWorkerName(saved.getName());
                    zoneRepository.save(newZone);
                });
            }

            // 이 작업자가 담당자로 지정된 다른 구역들의 이름도 최신화
            for (Zone z : zoneRepository.findAll()) {
                if (Objects.equals(z.getAssignedWorkerId(), saved.getId())) {
                    if (saved.getAssignedZoneId() == null) {
                        z.setAssignedWorkerId(null);
                        z.setAssignedWorkerName(null);
                    } else {
                        z.setAssignedWorkerName(saved.getName());
                    }
                    zoneRepository.save(z);
                }
            }

            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<Worker> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return workerRepository.findById(id).map(worker -> {
            String newStatus = body.get("status");
            if (newStatus != null && !newStatus.isBlank()) {
                worker.setStatus(newStatus);
            }
            return ResponseEntity.ok(workerRepository.save(worker));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (workerRepository.existsById(id)) {
            for (Zone z : zoneRepository.findAll()) {
                if (Objects.equals(z.getAssignedWorkerId(), id)) {
                    z.setAssignedWorkerId(null);
                    z.setAssignedWorkerName(null);
                    zoneRepository.save(z);
                }
            }
            workerRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
