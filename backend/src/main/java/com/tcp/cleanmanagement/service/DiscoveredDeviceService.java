package com.tcp.cleanmanagement.service;

import com.tcp.cleanmanagement.dto.DiscoveredDeviceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DiscoveredDeviceService {
    private final Map<String, DiscoveredDeviceResponse> discoveredMap = new ConcurrentHashMap<>();

    /**
     * 미등록 기기가 접속을 시도할 때 감지 정보를 기록하거나 갱신합니다.
     */
    public void recordDiscovery(String macAddress, String ipAddress) {
        if (macAddress == null || macAddress.isBlank()) return;
        String normalizedMac = macAddress.trim().toUpperCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();

        discoveredMap.compute(normalizedMac, (key, existing) -> {
            if (existing == null) {
                log.info("[DISCOVERY] 새로운 미등록 IoT 기기 감지: MAC={}, IP={}", normalizedMac, ipAddress);
                return DiscoveredDeviceResponse.builder()
                        .macAddress(normalizedMac)
                        .ipAddress(ipAddress)
                        .firstSeenAt(now)
                        .lastSeenAt(now)
                        .requestCount(1L)
                        .build();
            } else {
                existing.setLastSeenAt(now);
                existing.setRequestCount(existing.getRequestCount() + 1);
                if (ipAddress != null && !ipAddress.isBlank()) {
                    existing.setIpAddress(ipAddress);
                }
                return existing;
            }
        });
    }

    /**
     * 미등록 기기가 GPS 위치 정보를 보낸 경우 위치 좌표를 보관합니다.
     */
    public void recordLocation(String macAddress, Double latitude, Double longitude) {
        if (macAddress == null || macAddress.isBlank()) return;
        String normalizedMac = macAddress.trim().toUpperCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();

        discoveredMap.compute(normalizedMac, (key, existing) -> {
            if (existing == null) {
                return DiscoveredDeviceResponse.builder()
                        .macAddress(normalizedMac)
                        .firstSeenAt(now)
                        .lastSeenAt(now)
                        .requestCount(1L)
                        .latitude(latitude)
                        .longitude(longitude)
                        .build();
            } else {
                existing.setLastSeenAt(now);
                existing.setLatitude(latitude);
                existing.setLongitude(longitude);
                return existing;
            }
        });
    }

    /**
     * 관리자에게 제공할 감지된 미등록 기기 목록을 최근 접속순으로 반환합니다.
     */
    public List<DiscoveredDeviceResponse> getDiscoveredDevices() {
        List<DiscoveredDeviceResponse> list = new ArrayList<>(discoveredMap.values());
        list.sort(Comparator.comparing(DiscoveredDeviceResponse::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return list;
    }

    /**
     * 기기가 정식 등록되었거나 관리자가 무시/삭제했을 때 목록에서 제거합니다.
     */
    public void removeDevice(String macAddress) {
        if (macAddress == null) return;
        String normalizedMac = macAddress.trim().toUpperCase(Locale.ROOT);
        discoveredMap.remove(normalizedMac);
        log.info("[DISCOVERY] 미등록 기기 대기 목록에서 제외: MAC={}", normalizedMac);
    }

    public void clear() {
        discoveredMap.clear();
    }
}
