package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.dto.ChecklistToggleRequest;
import com.tcp.cleanmanagement.entity.DailyChecklist;
import com.tcp.cleanmanagement.repository.DailyChecklistRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/checklists")
@RequiredArgsConstructor
public class DailyChecklistController {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final DailyChecklistRepository checklistRepository;

    @GetMapping
    public ResponseEntity<List<DailyChecklist>> getChecklist(
            @RequestParam Long zoneId,
            @RequestParam(required = false) String date) {
        LocalDate targetDate = parseDate(date);
        return ResponseEntity.ok(checklistRepository.findByZoneIdAndCheckDate(zoneId, targetDate));
    }

    @PostMapping("/toggle")
    @Transactional
    public ResponseEntity<DailyChecklist> toggleItem(
            @RequestBody ChecklistToggleRequest request,
            HttpServletRequest httpRequest) {
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
        Long completedBy = request.getCompletedBy();
        if (completedBy == null) {
            Object authUserId = httpRequest.getAttribute("authenticatedUserId");
            completedBy = authUserId instanceof Number ? ((Number) authUserId).longValue() : 1L;
        }
        item.setCompletedBy(completedBy);
        DailyChecklist saved = checklistRepository.save(item);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/batch")
    @Transactional
    public ResponseEntity<List<DailyChecklist>> batchUpdate(
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        Object rawZoneId = body.get("zoneId");
        if (rawZoneId == null) {
            return ResponseEntity.badRequest().build();
        }
        Long zoneId = rawZoneId instanceof Number
                ? ((Number) rawZoneId).longValue()
                : Long.parseLong(String.valueOf(rawZoneId));
        String dateStr = body.get("date") != null ? String.valueOf(body.get("date")) : null;
        LocalDate targetDate = parseDate(dateStr);

        Long completedBy = null;
        if (body.get("completedBy") instanceof Number n) {
            completedBy = n.longValue();
        } else if (body.get("completedBy") != null) {
            try {
                completedBy = Long.parseLong(String.valueOf(body.get("completedBy")).trim());
            } catch (NumberFormatException ignored) {}
        }
        if (completedBy == null) {
            Object authUserId = httpRequest.getAttribute("authenticatedUserId");
            completedBy = authUserId instanceof Number ? ((Number) authUserId).longValue() : 1L;
        }

        Object rawItems = body.get("items");
        List<DailyChecklist> savedList = new ArrayList<>();
        if (rawItems instanceof Map<?, ?> itemsMap) {
            for (Map.Entry<?, ?> entry : itemsMap.entrySet()) {
                String itemKey = String.valueOf(entry.getKey());
                boolean isCompleted = Boolean.TRUE.equals(entry.getValue())
                        || "true".equalsIgnoreCase(String.valueOf(entry.getValue()));
                DailyChecklist item = checklistRepository.findByZoneIdAndCheckDateAndItemKey(zoneId, targetDate, itemKey)
                        .orElseGet(() -> DailyChecklist.builder()
                                .zoneId(zoneId)
                                .checkDate(targetDate)
                                .itemKey(itemKey)
                                .build());
                item.setCompleted(isCompleted);
                item.setCompletedBy(completedBy);
                savedList.add(checklistRepository.save(item));
            }
        }
        return ResponseEntity.ok(savedList);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now(KST);
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            return LocalDate.now(KST);
        }
    }
}
