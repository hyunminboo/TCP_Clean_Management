package com.tcp.cleanmanagement.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserInfoResponse user) {}
