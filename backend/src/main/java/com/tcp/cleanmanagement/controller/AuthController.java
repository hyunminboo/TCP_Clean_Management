package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.dto.LoginRequest;
import com.tcp.cleanmanagement.dto.LoginResponse;
import com.tcp.cleanmanagement.entity.User;
import com.tcp.cleanmanagement.entity.ZonePermission;
import com.tcp.cleanmanagement.enums.Role;
import com.tcp.cleanmanagement.repository.UserRepository;
import com.tcp.cleanmanagement.repository.ZonePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository userRepository;
    private final ZonePermissionRepository zonePermissionRepository;

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            return ResponseEntity.badRequest().body("아이디와 비밀번호를 입력해주세요.");
        }

        String uname = request.getUsername().trim();
        Optional<User> userOpt = userRepository.findByUsername(uname);
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            if (!user.getPassword().equals(request.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("비밀번호가 일치하지 않습니다.");
            }
        } else {
            String lower = uname.toLowerCase(Locale.ROOT);
            Role role = lower.contains("cleaner") ? Role.CLEANER
                    : (lower.contains("dev") ? Role.DEVELOPER : Role.ADMIN);
            user = userRepository.save(User.builder()
                    .username(uname)
                    .password(request.getPassword())
                    .role(role)
                    .build());
        }

        List<Long> zoneIds = zonePermissionRepository.findByUserId(user.getId()).stream()
                .filter(zp -> zp.getZone() != null)
                .map(zp -> zp.getZone().getId())
                .toList();

        String token = "Bearer tcp-token-" + user.getId() + ":" + user.getRole().name() + ":" + user.getUsername() + ":" + UUID.randomUUID();
        return ResponseEntity.ok(LoginResponse.builder()
                .token(token)
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .assignedZoneIds(zoneIds)
                .build());
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증되지 않은 사용자입니다.");
        }
        String raw = authHeader.substring("Bearer ".length()).trim();
        User resolvedUser = null;
        Role fallbackRole = Role.ADMIN;
        String fallbackUsername = "admin";
        Long fallbackId = 1L;

        if (raw.startsWith("tcp-token-")) {
            String body = raw.substring("tcp-token-".length());
            String[] parts = body.split(":", 4);
            if (parts.length >= 3) {
                try {
                    Long uid = Long.parseLong(parts[0]);
                    resolvedUser = userRepository.findById(uid).orElse(null);
                    fallbackId = uid;
                } catch (NumberFormatException ignored) {}
                try {
                    fallbackRole = Role.valueOf(parts[1]);
                } catch (IllegalArgumentException ignored) {}
                fallbackUsername = parts[2];
                if (resolvedUser == null) {
                    resolvedUser = userRepository.findByUsername(fallbackUsername).orElse(null);
                }
            }
        } else if (raw.startsWith("mock-jwt-token-")) {
            String roleStr = raw.substring("mock-jwt-token-".length()).trim().toUpperCase(Locale.ROOT);
            try {
                fallbackRole = Role.valueOf(roleStr);
            } catch (IllegalArgumentException ignored) {}
            fallbackUsername = fallbackRole == Role.DEVELOPER ? "developer"
                    : fallbackRole == Role.CLEANER ? "cleaner" : "admin";
            resolvedUser = userRepository.findByUsername(fallbackUsername).orElse(null);
        }

        if (resolvedUser != null) {
            List<Long> zoneIds = zonePermissionRepository.findByUserId(resolvedUser.getId()).stream()
                    .filter(zp -> zp.getZone() != null)
                    .map(zp -> zp.getZone().getId())
                    .toList();
            return ResponseEntity.ok(LoginResponse.builder()
                    .token(authHeader)
                    .id(resolvedUser.getId())
                    .username(resolvedUser.getUsername())
                    .role(resolvedUser.getRole())
                    .assignedZoneIds(zoneIds)
                    .build());
        }

        return ResponseEntity.ok(LoginResponse.builder()
                .token(authHeader)
                .id(fallbackId)
                .username(fallbackUsername)
                .role(fallbackRole)
                .assignedZoneIds(List.of())
                .build());
    }
}
