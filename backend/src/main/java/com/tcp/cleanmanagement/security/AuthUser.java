package com.tcp.cleanmanagement.security;

import com.tcp.cleanmanagement.enums.Role;

import java.util.Set;

/**
 * JWT 검증을 통과한 현재 요청의 사용자 정보.
 * 컨트롤러에서는 {@code @RequestAttribute(AuthUser.ATTR) AuthUser authUser} 로 주입받는다.
 *
 * @param zoneIds 담당 구역 ID. DEVELOPER 는 전체 접근이므로 비어 있어도 무방하다.
 */
public record AuthUser(Long id, String username, Role role, Set<Long> zoneIds) {

    public static final String ATTR = "authUser";

    /** 전체 구역 접근 가능 여부 (DEVELOPER 만 해당) */
    public boolean hasAllZoneAccess() {
        return role == Role.DEVELOPER;
    }

    public boolean canAccessZone(Long zoneId) {
        return hasAllZoneAccess() || (zoneId != null && zoneIds.contains(zoneId));
    }
}
