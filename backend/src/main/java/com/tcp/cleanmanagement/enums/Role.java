package com.tcp.cleanmanagement.enums;

/**
 * 사용자 역할
 * - DEVELOPER : 시스템 운영자. 전체 구역 접근, 시설/센서/시스템/사용자 관리
 * - ADMIN     : 현장 관리자. 담당 구역(ZonePermission)만 접근, 임계값/알림/청소 관리
 * - CLEANER   : 청소 담당자. 담당 구역만 접근, 알림 확인 및 청소 기록 등록
 */
public enum Role { DEVELOPER, ADMIN, CLEANER }
