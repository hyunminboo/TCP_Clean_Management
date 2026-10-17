package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.entity.CleaningRecord;
import com.tcp.cleanmanagement.repository.CleaningRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/cleaning-records")
@RequiredArgsConstructor
public class CleaningRecordController {
    private final CleaningRecordRepository cleaningRecordRepository;

    @GetMapping
    public ResponseEntity<List<CleaningRecord>> list(
            @RequestParam(required = false) Long zoneId) {
        if (zoneId != null) {
            return ResponseEntity.ok(cleaningRecordRepository.findByZoneIdOrderByCreatedAtDesc(zoneId));
        }
        return ResponseEntity.ok(cleaningRecordRepository.findAllByOrderByCreatedAtDesc());
    }

    @PostMapping
    public ResponseEntity<CleaningRecord> create(@RequestBody CleaningRecord record) {
        record.setId(null);
        CleaningRecord saved = cleaningRecordRepository.save(record);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (cleaningRecordRepository.existsById(id)) {
            cleaningRecordRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
