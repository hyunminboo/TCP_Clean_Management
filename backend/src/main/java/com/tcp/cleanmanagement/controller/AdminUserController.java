package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.entity.SystemSetting;
import com.tcp.cleanmanagement.entity.User;
import com.tcp.cleanmanagement.entity.Zone;
import com.tcp.cleanmanagement.entity.ZonePermission;
import com.tcp.cleanmanagement.enums.Role;
import com.tcp.cleanmanagement.repository.ActionLogRepository;
import com.tcp.cleanmanagement.repository.SystemSettingRepository;
import com.tcp.cleanmanagement.repository.UserRepository;
import com.tcp.cleanmanagement.repository.ZonePermissionRepository;
import com.tcp.cleanmanagement.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final ZonePermissionRepository zonePermissionRepository;
    private final ZoneRepository zoneRepository;
    private final ActionLogRepository actionLogRepository;
    private final SystemSettingRepository systemSettingRepository;

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> listUsers() {
        List<Map<String, Object>> result = userRepository.findAll().stream()
                .map(this::userToMap)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/users")
    @Transactional
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
        String username = body.get("username") != null ? String.valueOf(body.get("username")).trim() : "";
        String password = body.get("password") != null ? String.valueOf(body.get("password")).trim() : "1234";
        String roleStr = body.get("role") != null ? String.valueOf(body.get("role")).trim().toUpperCase() : "CLEANER";
        if (username.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        Role role = parseRole(roleStr);
        User saved = userRepository.save(User.builder()
                .username(username)
                .password(password.isEmpty() ? "1234" : password)
                .role(role)
                .build());

        syncZonePermissions(saved, body.get("assignedZoneIds"));
        return ResponseEntity.status(HttpStatus.CREATED).body(userToMap(saved));
    }

    @PutMapping("/users/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (body.get("username") != null && !String.valueOf(body.get("username")).isBlank()) {
            String newUsername = String.valueOf(body.get("username")).trim();
            userRepository.findByUsername(newUsername)
                    .filter(u -> !u.getId().equals(id))
                    .ifPresent(u -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
                    });
            user.setUsername(newUsername);
        }
        if (body.get("password") != null && !String.valueOf(body.get("password")).isBlank()) {
            user.setPassword(String.valueOf(body.get("password")).trim());
        }
        if (body.get("role") != null && !String.valueOf(body.get("role")).isBlank()) {
            user.setRole(parseRole(String.valueOf(body.get("role")).trim().toUpperCase()));
        }
        User saved = userRepository.save(user);
        if (body.containsKey("assignedZoneIds")) {
            syncZonePermissions(saved, body.get("assignedZoneIds"));
        }
        return ResponseEntity.ok(userToMap(saved));
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        zonePermissionRepository.deleteByUserId(id);
        actionLogRepository.deleteByAdminId(id);
        for (SystemSetting s : systemSettingRepository.findAll()) {
            if (id.equals(s.getUpdatedBy())) {
                s.setUpdatedBy(null);
                systemSettingRepository.save(s);
            }
        }
        userRepository.delete(user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/zone-permissions")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> listZonePermissions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long zoneId) {
        List<ZonePermission> perms;
        if (userId != null) {
            perms = zonePermissionRepository.findByUserId(userId);
        } else if (zoneId != null) {
            perms = zonePermissionRepository.findByZoneId(zoneId);
        } else {
            perms = zonePermissionRepository.findAll();
        }
        return ResponseEntity.ok(perms.stream().map(this::permToMap).toList());
    }

    @PostMapping("/zone-permissions")
    @Transactional
    public ResponseEntity<Map<String, Object>> grantZonePermission(@RequestBody Map<String, Object> body) {
        Long userId = toLong(body.get("userId"));
        Long zoneId = toLong(body.get("zoneId"));
        if (userId == null || zoneId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId and zoneId are required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone not found"));

        ZonePermission perm = zonePermissionRepository.findByUserIdAndZoneId(userId, zoneId)
                .orElseGet(() -> zonePermissionRepository.save(ZonePermission.builder()
                        .user(user)
                        .zone(zone)
                        .build()));
        return ResponseEntity.status(HttpStatus.CREATED).body(permToMap(perm));
    }

    @DeleteMapping("/zone-permissions/{id}")
    @Transactional
    public ResponseEntity<Void> revokeZonePermission(@PathVariable Long id) {
        if (!zonePermissionRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        zonePermissionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void syncZonePermissions(User user, Object rawZoneIds) {
        if (!(rawZoneIds instanceof List<?> list)) return;
        zonePermissionRepository.deleteByUserId(user.getId());
        zonePermissionRepository.flush();
        for (Object item : list) {
            Long zId = toLong(item);
            if (zId == null) continue;
            zoneRepository.findById(zId).ifPresent(zone -> {
                zonePermissionRepository.save(ZonePermission.builder()
                        .user(user)
                        .zone(zone)
                        .build());
            });
        }
    }

    private Role parseRole(String raw) {
        try {
            return Role.valueOf(raw);
        } catch (Exception e) {
            return Role.CLEANER;
        }
    }

    private Long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        if (val != null) {
            try {
                return Long.parseLong(String.valueOf(val).trim());
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Map<String, Object> userToMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("role", u.getRole() != null ? u.getRole().name() : "CLEANER");
        m.put("createdAt", u.getCreatedAt());
        List<ZonePermission> perms = zonePermissionRepository.findByUserId(u.getId());
        List<Long> zoneIds = new ArrayList<>();
        List<String> zoneNames = new ArrayList<>();
        for (ZonePermission p : perms) {
            if (p.getZone() != null) {
                zoneIds.add(p.getZone().getId());
                zoneNames.add(p.getZone().getName());
            }
        }
        m.put("assignedZoneIds", zoneIds);
        m.put("assignedZoneNames", zoneNames);
        return m;
    }

    private Map<String, Object> permToMap(ZonePermission p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("userId", p.getUser() != null ? p.getUser().getId() : null);
        m.put("username", p.getUser() != null ? p.getUser().getUsername() : null);
        m.put("zoneId", p.getZone() != null ? p.getZone().getId() : null);
        m.put("zoneName", p.getZone() != null ? p.getZone().getName() : null);
        m.put("createdAt", p.getCreatedAt());
        return m;
    }
}
