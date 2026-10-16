package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.dto.ChecklistToggleRequest;
import com.tcp.cleanmanagement.entity.DailyChecklist;
import com.tcp.cleanmanagement.repository.DailyChecklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/checklists")
@RequiredArgsConstructor
public class DailyChecklistController {
    private final DailyChecklistRepository checklistRepository;

    @GetMapping
    public ResponseEntity<List<DailyChecklist>> getChecklist(
            @RequestParam Long zoneId,
            @RequestParam(required = false) String date) {
        LocalDate targetDate = parseDate(date);
        return ResponseEntity.ok(checklistRepository.findByZoneIdAndCheckDate(zoneId, targetDate));
    }

    @PostMapping("/toggle")
    public ResponseEntity<DailyChecklist> toggleItem(@RequestBody ChecklistToggleRequest request) {
        if (request.getZoneId() == null || request.getItemKey() == null) {
            return ResponseEntity.badRequest().build();
        }
        LocalDate targetDate = parseDate(request.getDate());
        DailyChecklist item = checklistRepository.findByZoneIdAndCheckDateAndItemKey(
                request.getZoneId(), targetDate, request.getItemKey())
                .orElseGet(() -> DailyChecklist.builder()
                        .zoneId(request.getZoneId())
                        .checkDate(targetDate)
                        .itemKey(request.getItemKey())
                        .build());

        if (request.getIsCompleted() != null) {
            item.setCompleted(request.getIsCompleted());
        } else {
            item.setCompleted(!item.isCompleted());
        }
        if (request.getCompletedBy() != null) {
            item.setCompletedBy(request.getCompletedBy());
        }
        DailyChecklist saved = checklistRepository.save(item);
        return ResponseEntity.ok(saved);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }
}
