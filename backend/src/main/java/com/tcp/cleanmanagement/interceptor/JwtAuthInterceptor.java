package com.tcp.cleanmanagement.interceptor;

import com.tcp.cleanmanagement.entity.User;
import com.tcp.cleanmanagement.enums.Role;
import com.tcp.cleanmanagement.repository.UserRepository;
import com.tcp.cleanmanagement.repository.ZonePermissionRepository;
import com.tcp.cleanmanagement.security.AuthUser;
import com.tcp.cleanmanagement.security.JwtTokenProvider;
import com.tcp.cleanmanagement.security.RequireRole;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 1) Authorization: Bearer 토큰 서명/만료 검증 → 실패 시 401
 * 2) DB 에서 사용자/담당 구역을 조회해 {@link AuthUser} 를 request attribute 로 저장
 *    (역할·구역 변경이 재로그인 없이 즉시 반영되도록 토큰의 role 대신 DB 값을 사용)
 * 3) 핸들러의 {@link RequireRole} 과 비교 → 불일치 시 403
 */
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final ZonePermissionRepository zonePermissionRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // CORS preflight 는 토큰 없이 통과 (실제 요청에서 검증)
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(response, HttpStatus.UNAUTHORIZED, "Missing or invalid token");
        }

        JwtTokenProvider.ParsedToken parsed;
        try {
            parsed = jwtTokenProvider.parse(authHeader.substring(7));
        } catch (JwtException e) {
            return reject(response, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }

        Optional<User> userOpt = userRepository.findById(parsed.userId());
        if (userOpt.isEmpty()) {
            return reject(response, HttpStatus.UNAUTHORIZED, "User no longer exists");
        }
        User user = userOpt.get();

        Set<Long> zoneIds = user.getRole() == Role.DEVELOPER
                ? Set.of()
                : new HashSet<>(zonePermissionRepository.findZoneIdsByUserId(user.getId()));
        AuthUser authUser = new AuthUser(user.getId(), user.getUsername(), user.getRole(), Set.copyOf(zoneIds));
        request.setAttribute(AuthUser.ATTR, authUser);

        if (handler instanceof HandlerMethod hm) {
            RequireRole required = Optional.ofNullable(hm.getMethodAnnotation(RequireRole.class))
                    .orElseGet(() -> hm.getBeanType().getAnnotation(RequireRole.class));
            if (required != null && Arrays.stream(required.value()).noneMatch(r -> r == authUser.role())) {
                return reject(response, HttpStatus.FORBIDDEN, "Insufficient role");
            }
        }
        return true;
    }

    private boolean reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":" + status.value() + ",\"message\":\"" + message + "\"}");
        return false;
    }
}
