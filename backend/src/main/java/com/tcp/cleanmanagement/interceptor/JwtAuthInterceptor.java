package com.tcp.cleanmanagement.interceptor;

import com.tcp.cleanmanagement.enums.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("Unauthorized: Missing or invalid token");
            return false;
        }
        String raw = authHeader.substring("Bearer ".length()).trim();
        if (raw.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("Unauthorized: Empty token");
            return false;
        }

        Long userId = 1L;
        String username = "admin";
        Role role = Role.ADMIN;

        if (raw.startsWith("tcp-token-")) {
            String body = raw.substring("tcp-token-".length());
            String[] parts = body.split(":", 4);
            if (parts.length >= 3) {
                try {
                    userId = Long.parseLong(parts[0]);
                } catch (NumberFormatException ignored) {}
                try {
                    role = Role.valueOf(parts[1]);
                } catch (IllegalArgumentException ignored) {}
                username = parts[2];
            }
        } else if (raw.startsWith("mock-jwt-token-")) {
            String roleStr = raw.substring("mock-jwt-token-".length()).trim().toUpperCase(Locale.ROOT);
            try {
                role = Role.valueOf(roleStr);
            } catch (IllegalArgumentException ignored) {}
            username = role == Role.DEVELOPER ? "developer" : role == Role.CLEANER ? "cleaner" : "admin";
        }

        request.setAttribute("authenticatedUserId", userId);
        request.setAttribute("authenticatedUsername", username);
        request.setAttribute("authenticatedRole", role);
        return true;
    }
}
