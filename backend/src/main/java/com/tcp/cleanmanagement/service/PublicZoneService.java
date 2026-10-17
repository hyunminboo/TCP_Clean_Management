package com.tcp.cleanmanagement.service;

import com.tcp.cleanmanagement.dto.ZonePublicResponse;
import com.tcp.cleanmanagement.dto.ZoneStatusResponse;
import com.tcp.cleanmanagement.entity.CleaningRecord;
import com.tcp.cleanmanagement.entity.SensorDataRaw;
import com.tcp.cleanmanagement.entity.Worker;
import com.tcp.cleanmanagement.entity.Zone;
import com.tcp.cleanmanagement.enums.MetricCode;
import com.tcp.cleanmanagement.enums.SensorModel;
import com.tcp.cleanmanagement.enums.ZoneType;
import com.tcp.cleanmanagement.repository.CleaningRecordRepository;
import com.tcp.cleanmanagement.repository.SensorDataRawRepository;
import com.tcp.cleanmanagement.repository.WorkerRepository;
import com.tcp.cleanmanagement.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicZoneService {
    private final ZoneRepository zoneRepository;
    private final SensorDataRawRepository sensorDataRawRepository;
    private final WorkerRepository workerRepository;
    private final CleaningRecordRepository cleaningRecordRepository;

    @Transactional(readOnly = true)
    public List<ZonePublicResponse> getZones(ZoneType type, Double lat, Double lng, Double radius) {
        List<Zone> zones;

        // 위치 정보가 들어왔다면 반경 검색 수행, 아니면 전체 반환
        if (lat != null && lng != null && radius != null) {
            if (type != null) {
                zones = zoneRepository.findZonesByTypeAndRadius(type.name(), lat, lng, radius);
            } else {
                zones = zoneRepository.findZonesWithinRadius(lat, lng, radius);
            }
        } else {
            if (type != null) {
                zones = zoneRepository.findByZoneType(type);
            } else {
                zones = zoneRepository.findAll();
            }
        }

        return zones.stream()
            .map(this::mapToResponseWithSensorData)
            .collect(Collectors.toList());
    }

    private ZonePublicResponse mapToResponseWithSensorData(Zone zone) {
        ZonePublicResponse.ZonePublicResponseBuilder builder = ZonePublicResponse.builder()
                .zoneId(zone.getId())
                .name(zone.getName())
                .address(zone.getAddress())
                .zoneType(zone.getZoneType() != null ? zone.getZoneType().name() : "TOILET")
                .latitude(zone.getLatitude())
                .longitude(zone.getLongitude())
                .isOurSolution(zone.getIsOurSolution())
                .isActive(zone.getIsActive() != null ? zone.getIsActive() : true)
                .pleasantThreshold(zone.getPleasantThreshold())
                .needsVentilationThreshold(zone.getNeedsVentilationThreshold());

        // 담당 청소관리인 매핑 (Zone 우선, 없으면 Worker 테이블의 assignedZoneId 조회)
        if (zone.getAssignedWorkerName() != null && !zone.getAssignedWorkerName().isBlank()) {
            builder.assignedWorkerId(zone.getAssignedWorkerId())
                   .assignedWorkerName(zone.getAssignedWorkerName());
        } else {
            Optional<Worker> assignedWorker = workerRepository.findFirstByAssignedZoneIdOrderByIdAsc(zone.getId());
            assignedWorker.ifPresent(w -> builder
                    .assignedWorkerId(w.getId())
                    .assignedWorkerName(w.getName()));
        }

        // 최근 청소 기록 매핑
        List<CleaningRecord> records = cleaningRecordRepository.findByZoneIdOrderByCreatedAtDesc(zone.getId());
        if (records != null && !records.isEmpty()) {
            CleaningRecord latestRecord = records.get(0);
            if (latestRecord.getCreatedAt() != null) {
                builder.lastCleanedAt(latestRecord.getCreatedAt().toString());
            }
            if (latestRecord.getWorker() != null && !latestRecord.getWorker().isBlank()) {
                builder.lastCleanedWorker(latestRecord.getWorker());
            }
        }

        Optional<SensorDataRaw> latestMq135 = latestSensorReading(zone.getId(), SensorModel.MQ135);
        if (latestMq135.isEmpty()) {
            latestMq135 = latestReading(zone.getId(), MetricCode.GAS);
        }
        Optional<SensorDataRaw> latestMq7 = latestSensorReading(zone.getId(), SensorModel.MQ7);

        latestReading(zone.getId(), MetricCode.TEMPERATURE)
                .ifPresent(data -> builder.temp(data.getMeasuredValue().floatValue()));
        latestReading(zone.getId(), MetricCode.HUMIDITY)
                .ifPresent(data -> builder.humidity(data.getMeasuredValue().floatValue()));
        latestMq7
                .ifPresent(data -> builder.mq7Level(data.getMeasuredValue().floatValue()));

        if (latestMq135.isPresent()) {
            SensorDataRaw data = latestMq135.get();
            float gasLevel = data.getMeasuredValue().floatValue();
            builder.gasLevel(gasLevel);

            if (zone.getZoneType() == ZoneType.TOILET) {
                float pleasant = zone.getPleasantThreshold() != null ? zone.getPleasantThreshold() : 300.0f;
                float vent = zone.getNeedsVentilationThreshold() != null ? zone.getNeedsVentilationThreshold() : 600.0f;
                if (gasLevel >= vent) {
                    builder.status("Needs Cleaning").color("#ef4444");
                } else if (gasLevel >= pleasant) {
                    builder.status("Caution").color("#f59e0b");
                } else {
                    builder.status("Clean").color("#10b981");
                }
            } else {
                boolean isCrowded = gasLevel > 200.0f;
                builder.status(isCrowded ? "Crowded" : "Spacious");
                builder.color(isCrowded ? "#f59e0b" : "#3b82f6");
            }
        } else {
            builder.status("No Data");
            builder.color("#9ca3af");
        }

        return builder.build();
    }

    private Optional<SensorDataRaw> latestReading(Long zoneId, MetricCode metricCode) {
        return sensorDataRawRepository
                .findFirstByMetricSensorZoneIdAndMetricMetricCodeOrderByMeasuredAtDescIdDesc(zoneId, metricCode);
    }

    private Optional<SensorDataRaw> latestSensorReading(Long zoneId, SensorModel sensorModel) {
        return sensorDataRawRepository
                .findFirstByMetricSensorZoneIdAndMetricSensorSensorModelOrderByMeasuredAtDescIdDesc(zoneId, sensorModel);
    }

    @Transactional(readOnly = true)
    public ZoneStatusResponse getZoneStatus(Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found"));
        ZonePublicResponse mapped = mapToResponseWithSensorData(zone);
        String rawStatus = mapped.getStatus();
        String normStatus = "Needs Cleaning".equals(rawStatus) ? "DANGER"
                : ("Caution".equals(rawStatus) || "Crowded".equals(rawStatus)) ? "WARN"
                : "No Data".equals(rawStatus) ? "OFFLINE" : "NORMAL";
        String msg = "DANGER".equals(normStatus) ? "환기 및 긴급 청소 점검이 필요한 상태입니다."
                : "WARN".equals(normStatus) ? "실내 공기질 주의 단계입니다."
                : "OFFLINE".equals(normStatus) ? "센서 측정 데이터 수신 대기 중입니다."
                : "이용하기 쾌적한 상태입니다.";

        return ZoneStatusResponse.builder()
                .zoneId(zone.getId())
                .name(zone.getName())
                .status(normStatus)
                .statusMessage(msg)
                .build();
    }
}
