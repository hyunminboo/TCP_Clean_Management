package com.tcp.cleanmanagement.service;

import com.tcp.cleanmanagement.dto.LoginRequest;
import com.tcp.cleanmanagement.dto.LoginResponse;
import com.tcp.cleanmanagement.dto.UserInfoResponse;
import com.tcp.cleanmanagement.entity.User;
import com.tcp.cleanmanagement.enums.Role;
import com.tcp.cleanmanagement.repository.UserRepository;
import com.tcp.cleanmanagement.repository.ZonePermissionRepository;
import com.tcp.cleanmanagement.security.AuthUser;
import com.tcp.cleanmanagement.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ZonePermissionRepository zonePermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 아이디/비밀번호 검증 후 토큰 발급.
     * 아이디 없음과 비밀번호 불일치를 구분하지 않고 동일하게 empty 를 반환한다(계정 존재 여부 노출 방지).
     */
    @Transactional(readOnly = true)
    public Optional<LoginResponse> login(LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .filter(user -> passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .map(user -> new LoginResponse(
                        jwtTokenProvider.createToken(user),
                        "Bearer",
                        jwtTokenProvider.getValiditySeconds(),
                        UserInfoResponse.from(toAuthUser(user))));
    }

    private AuthUser toAuthUser(User user) {
        Set<Long> zoneIds = user.getRole() == Role.DEVELOPER
                ? Set.of()
                : Set.copyOf(zonePermissionRepository.findZoneIdsByUserId(user.getId()));
        return new AuthUser(user.getId(), user.getUsername(), user.getRole(), zoneIds);
    }
}
