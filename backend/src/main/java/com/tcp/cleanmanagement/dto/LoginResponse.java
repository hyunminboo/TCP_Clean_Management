package com.tcp.cleanmanagement.dto;

import com.tcp.cleanmanagement.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long id;
    private String username;
    private Role role;
    private List<Long> assignedZoneIds;
}
