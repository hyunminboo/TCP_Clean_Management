package com.tcp.cleanmanagement.service;

import com.tcp.cleanmanagement.dto.SensorCreateRequest;
import com.tcp.cleanmanagement.dto.ZoneCreateRequest;
import com.tcp.cleanmanagement.entity.DeviceSet;
import com.tcp.cleanmanagement.entity.Sensor;
import com.tcp.cleanmanagement.entity.SensorMetric;
import com.tcp.cleanmanagement.entity.UpstashDevice;
import com.tcp.cleanmanagement.entity.Worker;
import com.tcp.cleanmanagement.entity.Zone;
import com.tcp.cleanmanagement.enums.SensorStatus;
import com.tcp.cleanmanagement.enums.SensorType;
import com.tcp.cleanmanagement.repository.ActionLogRepository;
import com.tcp.cleanmanagement.repository.AlertRepository;
import com.tcp.cleanmanagement.repository.CleaningRecordRepository;
import com.tcp.cleanmanagement.repository.DailyChecklistRepository;
import com.tcp.cleanmanagement.repository.DeviceSetHistoryRepository;
import com.tcp.cleanmanagement.repository.DeviceSetRepository;
import com.tcp.cleanmanagement.repository.SensorDataAggregatedRepository;
import com.tcp.cleanmanagement.repository.SensorDataRawRepository;
import com.tcp.cleanmanagement.repository.SensorMetricRepository;
import com.tcp.cleanmanagement.repository.SensorRepository;
import com.tcp.cleanmanagement.repository.UpstashDeviceRepository;
import com.tcp.cleanmanagement.repository.UpstashImportedSampleRepository;
import com.tcp.cleanmanagement.repository.WorkerRepository;
import com.tcp.cleanmanagement.repository.ZonePermissionRepository;
import com.tcp.cleanmanagement.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DevZoneService {
    private final ZoneRepository zoneRepository;
    private final SensorRepository sensorRepository;
    private final SensorMetricRepository sensorMetricRepository;
    private final SensorDataRawRepository sensorDataRawRepository;
    private final SensorDataAggregatedRepository sensorDataAggregatedRepository;
    private final DeviceSetRepository deviceSetRepository;
    private final DeviceSetHistoryRepository deviceSetHistoryRepository;
    private final AlertRepository alertRepository;
    private final ActionLogRepository actionLogRepository;
    private final UpstashDeviceRepository upstashDeviceRepository;
    private final UpstashImportedSampleRepository upstashImportedSampleRepository;
    private final DailyChecklistRepository dailyChecklistRepository;
    private final CleaningRecordRepository cleaningRecordRepository;
    private final ZonePermissionRepository zonePermissionRepository;
    private final WorkerRepository workerRepository;

    @Transactional
    public Long createZone(ZoneCreateRequest request) {
        Zone zone = Zone.builder()
                .name(request.getName())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .zoneType(request.getZoneType())
                .isOurSolution(request.getIsOurSolution() != null ? request.getIsOurSolution() : true)
                .pleasantThreshold(request.getPleasantThreshold())
                .needsVentilationThreshold(request.getNeedsVentilationThreshold())
                .assignedWorkerId(request.getAssignedWorkerId())
                .assignedWorkerName(request.getAssignedWorkerName())
                .build();

        Zone saved = zoneRepository.save(zone);
        if (request.getAssignedWorkerId() != null) {
            workerRepository.findById(request.getAssignedWorkerId()).ifPresent(worker -> {
                saved.setAssignedWorkerName(worker.getName());
                zoneRepository.save(saved);
                worker.setAssignedZoneId(saved.getId());
                workerRepository.save(worker);
            });
        }
        return saved.getId();
    }

    @Transactional
    public Long createSensor(SensorCreateRequest request) {
        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid Zone ID"));

        Sensor sensor = Sensor.builder()
                .zone(zone)
                .sensorType(request.getSensorType() != null ? request.getSensorType() : SensorType.GAS)
                .sensorModel(request.getSensorModel())
                .status(request.getStatus() != null ? request.getStatus() : SensorStatus.ACTIVE)
                .build();

        return sensorRepository.save(sensor).getId();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSensors(Long zoneId) {
        List<Sensor> sensors = zoneId != null
                ? sensorRepository.findByZoneIdOrderByIdAsc(zoneId)
                : sensorRepository.findAll();
        return sensors.stream().map(this::sensorToMap).toList();
    }

    @Transactional
    public Map<String, Object> updateSensor(Long sensorId, SensorCreateRequest request) {
        Sensor sensor = sensorRepository.findById(sensorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sensor not found: " + sensorId));
        if (request.getZoneId() != null) {
            Zone zone = zoneRepository.findById(request.getZoneId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone not found: " + request.getZoneId()));
            sensor.setZone(zone);
        }
        if (request.getSensorType() != null) sensor.setSensorType(request.getSensorType());
        if (request.getSensorModel() != null) sensor.setSensorModel(request.getSensorModel());
        if (request.getStatus() != null) sensor.setStatus(request.getStatus());
        Sensor saved = sensorRepository.save(sensor);

        if (request.getPleasantThreshold() != null || request.getNeedsVentilationThreshold() != null) {
            for (SensorMetric metric : sensorMetricRepository.findBySensorId(sensorId)) {
                if (request.getPleasantThreshold() != null) {
                    metric.setPleasantThreshold(java.math.BigDecimal.valueOf(request.getPleasantThreshold()));
                }
                if (request.getNeedsVentilationThreshold() != null) {
                    metric.setNeedsVentilationThreshold(java.math.BigDecimal.valueOf(request.getNeedsVentilationThreshold()));
                }
                sensorMetricRepository.save(metric);
            }
        }
        return sensorToMap(saved);
    }

    @Transactional
    public void deleteSensor(Long sensorId) {
        Sensor sensor = sensorRepository.findById(sensorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sensor not found: " + sensorId));
        upstashDeviceRepository.unlinkTempHumSensor(sensorId);
        upstashDeviceRepository.unlinkGasSensor(sensorId);
        actionLogRepository.deleteBySensorId(sensorId);
        alertRepository.deleteBySensorId(sensorId);
        sensorDataRawRepository.deleteBySensorId(sensorId);
        sensorDataAggregatedRepository.deleteBySensorId(sensorId);
        sensorMetricRepository.deleteBySensorId(sensorId);
        sensorRepository.delete(sensor);
    }

    private Map<String, Object> sensorToMap(Sensor s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", s.getId());
        map.put("zoneId", s.getZone() != null ? s.getZone().getId() : null);
        map.put("zoneName", s.getZone() != null ? s.getZone().getName() : null);
        map.put("deviceSetId", s.getDeviceSet() != null ? s.getDeviceSet().getId() : null);
        map.put("setCode", s.getDeviceSet() != null ? s.getDeviceSet().getSetCode() : null);
        map.put("macAddress", s.getDeviceSet() != null ? s.getDeviceSet().getMacAddress() : null);
        map.put("sensorType", s.getSensorType() != null ? s.getSensorType().name() : null);
        map.put("sensorModel", s.getSensorModel() != null ? s.getSensorModel().name() : null);
        map.put("status", s.getStatus() != null ? s.getStatus().name() : "ACTIVE");
        map.put("createdAt", s.getCreatedAt());
        List<SensorMetric> metrics = sensorMetricRepository.findBySensorId(s.getId());
        map.put("metrics", metrics.stream().map(m -> {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", m.getId());
            mm.put("metricCode", m.getMetricCode() != null ? m.getMetricCode().name() : null);
            mm.put("unitCode", m.getUnitCode());
            mm.put("pleasantThreshold", m.getPleasantThreshold());
            mm.put("needsVentilationThreshold", m.getNeedsVentilationThreshold());
            return mm;
        }).toList());
        return map;
    }

    @Transactional
    public void deleteZone(Long id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid Zone ID: " + id));
        for (Worker w : workerRepository.findByAssignedZoneId(id)) {
            w.setAssignedZoneId(null);
            workerRepository.save(w);
        }
        zonePermissionRepository.deleteByZoneId(id);
        dailyChecklistRepository.deleteByZoneId(id);
        cleaningRecordRepository.deleteByZoneId(id);

        List<Sensor> zoneSensors = sensorRepository.findByZoneIdOrderByIdAsc(id);
        for (Sensor s : zoneSensors) {
            upstashDeviceRepository.unlinkTempHumSensor(s.getId());
            upstashDeviceRepository.unlinkGasSensor(s.getId());
            actionLogRepository.deleteBySensorId(s.getId());
            alertRepository.deleteBySensorId(s.getId());
            sensorDataRawRepository.deleteBySensorId(s.getId());
            sensorDataAggregatedRepository.deleteBySensorId(s.getId());
            sensorMetricRepository.deleteBySensorId(s.getId());
        }

        actionLogRepository.deleteByZoneId(id);
        alertRepository.unlinkHistoryByZoneId(id);
        alertRepository.deleteByZoneId(id);
        sensorDataRawRepository.unlinkByZoneId(id);
        sensorDataRawRepository.deleteByZoneId(id);
        sensorDataAggregatedRepository.deleteByZoneId(id);
        sensorMetricRepository.deleteByZoneId(id);

        for (UpstashDevice ud : upstashDeviceRepository.findByZoneId(id)) {
            upstashImportedSampleRepository.deleteByMacAddress(ud.getMacAddress());
        }
        upstashDeviceRepository.deleteByZoneId(id);

        List<DeviceSet> zoneSets = deviceSetRepository.findByZoneIdOrderByIdAsc(id);
        for (DeviceSet ds : zoneSets) {
            for (Sensor dsSensor : sensorRepository.findByDeviceSetIdOrderByIdAsc(ds.getId())) {
                dsSensor.setDeviceSet(null);
                sensorRepository.save(dsSensor);
            }
            sensorDataRawRepository.unlinkByDeviceSetId(ds.getId());
            alertRepository.unlinkHistoryByDeviceSetId(ds.getId());
            deviceSetHistoryRepository.deleteByDeviceSetId(ds.getId());
        }
        deviceSetHistoryRepository.deleteByZoneId(id);
        sensorRepository.deleteAll(zoneSensors);
        deviceSetRepository.deleteAll(zoneSets);
        zoneRepository.delete(zone);
    }

    @Transactional
    public Boolean toggleZoneActive(Long id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid Zone ID: " + id));
        boolean current = zone.getIsActive() != null ? zone.getIsActive() : true;
        zone.setIsActive(!current);
        return zoneRepository.save(zone).getIsActive();
    }

    @Transactional
    public Zone updateZone(Long id, ZoneCreateRequest request) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid Zone ID: " + id));
        if (request.getName() != null) zone.setName(request.getName());
        if (request.getAddress() != null) zone.setAddress(request.getAddress());
        if (request.getLatitude() != null) zone.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) zone.setLongitude(request.getLongitude());
        if (request.getZoneType() != null) zone.setZoneType(request.getZoneType());
        if (request.getIsOurSolution() != null) zone.setIsOurSolution(request.getIsOurSolution());
        if (request.getPleasantThreshold() != null) zone.setPleasantThreshold(request.getPleasantThreshold());
        if (request.getNeedsVentilationThreshold() != null) zone.setNeedsVentilationThreshold(request.getNeedsVentilationThreshold());
        if (request.getAssignedWorkerId() != null) {
            workerRepository.findById(request.getAssignedWorkerId()).ifPresent(worker -> {
                zone.setAssignedWorkerId(worker.getId());
                zone.setAssignedWorkerName(worker.getName());
                worker.setAssignedZoneId(zone.getId());
                workerRepository.save(worker);
            });
        } else if (request.getAssignedWorkerName() != null) {
            zone.setAssignedWorkerName(request.getAssignedWorkerName().isBlank() ? null : request.getAssignedWorkerName().trim());
        }
        return zoneRepository.save(zone);
    }

    @Transactional
    public Zone assignWorkerToZone(Long zoneId, Long workerId, String workerName) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid Zone ID: " + zoneId));

        if (workerId != null) {
            Worker worker = workerRepository.findById(workerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid Worker ID: " + workerId));
            zone.setAssignedWorkerId(worker.getId());
            zone.setAssignedWorkerName(worker.getName());
            worker.setAssignedZoneId(zoneId);
            workerRepository.save(worker);
        } else if (workerName != null && !workerName.isBlank()) {
            zone.setAssignedWorkerId(null);
            zone.setAssignedWorkerName(workerName.trim());
        } else {
            Long prevWorkerId = zone.getAssignedWorkerId();
            zone.setAssignedWorkerId(null);
            zone.setAssignedWorkerName(null);
            if (prevWorkerId != null) {
                workerRepository.findById(prevWorkerId).ifPresent(w -> {
                    if (Objects.equals(w.getAssignedZoneId(), zoneId)) {
                        w.setAssignedZoneId(null);
                        workerRepository.save(w);
                    }
                });
            }
            for (Worker w : workerRepository.findByAssignedZoneId(zoneId)) {
                w.setAssignedZoneId(null);
                workerRepository.save(w);
            }
        }
        return zoneRepository.save(zone);
    }
}
