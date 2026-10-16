package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.dto.LoginRequest;
import com.tcp.cleanmanagement.dto.LoginResponse;
import com.tcp.cleanmanagement.entity.User;
import com.tcp.cleanmanagement.enums.Role;
import com.tcp.cleanmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            return ResponseEntity.badRequest().body("아이디와 비밀번호를 입력해주세요.");
        }

        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            if (!user.getPassword().equals(request.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("비밀번호가 일치하지 않습니다.");
            }
        } else {
            Role role = request.getUsername().toLowerCase().contains("cleaner") ? Role.ADMIN : Role.DEVELOPER;
            user = userRepository.save(User.builder()
                    .username(request.getUsername())
                    .password(request.getPassword())
                    .role(role)
                    .build());
        }

        String token = "Bearer tcp-token-" + UUID.randomUUID().toString();
        return ResponseEntity.ok(LoginResponse.builder()
                .token(token)
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build());
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증되지 않은 사용자입니다.");
        }
        return ResponseEntity.ok(LoginResponse.builder()
                .token(authHeader)
                .id(1L)
                .username("admin")
                .role(Role.ADMIN)
                .build());
    }
}
