package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.dto.WorkerRequest;
import com.tcp.cleanmanagement.entity.Worker;
import com.tcp.cleanmanagement.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/workers")
@RequiredArgsConstructor
public class WorkerController {
    private final WorkerRepository workerRepository;

    @GetMapping
    public ResponseEntity<List<Worker>> list(@RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(workerRepository.findByStatus(status));
        }
        return ResponseEntity.ok(workerRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Worker> create(@RequestBody WorkerRequest request) {
        Worker worker = Worker.builder()
                .name(request.getName())
                .phone(request.getPhone() != null ? request.getPhone() : "")
                .assignedZoneId(request.getAssignedZoneId())
                .status(request.getStatus() != null ? request.getStatus() : "AVAILABLE")
                .role(request.getRole() != null ? request.getRole() : "CLEANER")
                .build();
        Worker saved = workerRepository.save(worker);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/{id}/status")
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
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (workerRepository.existsById(id)) {
            workerRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
