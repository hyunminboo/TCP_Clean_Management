package com.tcp.cleanmanagement.dto;

import com.tcp.cleanmanagement.enums.Role;
import com.tcp.cleanmanagement.security.AuthUser;

import java.util.List;

/** 로그인 사용자 정보 (프론트 권한 판단용). 비밀번호 등 민감정보는 포함하지 않는다. */
public record UserInfoResponse(Long id, String username, Role role, List<Long> zoneIds) {

    public static UserInfoResponse from(AuthUser u) {
        return new UserInfoResponse(u.id(), u.username(), u.role(), u.zoneIds().stream().sorted().toList());
    }
}
