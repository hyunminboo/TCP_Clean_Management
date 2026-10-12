package com.tcp.cleanmanagement.controller;

import com.tcp.cleanmanagement.dto.LoginRequest;
import com.tcp.cleanmanagement.dto.UserInfoResponse;
import com.tcp.cleanmanagement.security.AuthUser;
import com.tcp.cleanmanagement.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 로그인 (인증 불필요) */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", 401, "message", "아이디 또는 비밀번호가 올바르지 않습니다.")));
    }

    /** 현재 토큰의 사용자 정보 (JwtAuthInterceptor 적용) */
    @GetMapping("/me")
    public UserInfoResponse me(@RequestAttribute(AuthUser.ATTR) AuthUser authUser) {
        return UserInfoResponse.from(authUser);
    }
}
