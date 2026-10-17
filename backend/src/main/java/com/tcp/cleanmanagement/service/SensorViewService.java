package com.tcp.cleanmanagement.service;

import com.tcp.cleanmanagement.dto.UpstashSample;
import com.tcp.cleanmanagement.entity.DeviceSet;
import com.tcp.cleanmanagement.entity.SensorDataRaw;
import com.tcp.cleanmanagement.entity.SystemSetting;
import com.tcp.cleanmanagement.entity.UpstashDevice;
import com.tcp.cleanmanagement.entity.Zone;
import com.tcp.cleanmanagement.enums.MetricCode;
import com.tcp.cleanmanagement.enums.SensorModel;
import com.tcp.cleanmanagement.repository.DeviceSetRepository;
import com.tcp.cleanmanagement.repository.SensorDataRawRepository;
import com.tcp.cleanmanagement.repository.SystemSettingRepository;
import com.tcp.cleanmanagement.repository.UpstashDeviceRepository;
import com.tcp.cleanmanagement.repository.UpstashImportedSampleRepository;
import com.tcp.cleanmanagement.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SensorViewService {
    private final UpstashDeviceRepository devices;
    private final UpstashImportedSampleRepository samples;
    private final DeviceSetRepository deviceSetRepository;
    private final SensorDataRawRepository sensorDataRawRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final ZoneRepository zoneRepository;
    private final JsonMapper mapper;

    private long getOfflineTimeoutSeconds() {
        try {
            return systemSettingRepository.findById("offline_timeout_seconds")
                    .map(SystemSetting::getSettingValue)
                    .map(Long::parseLong)
                    .filter(v -> v >= 10 && v <= 86400)
                    .orElse(300L);
        } catch (Exception e) {
            return 300L;
        }
    }

    private static String virtualMacForZone(Long zoneId) {
        long id = zoneId != null ? zoneId : 0L;
        return String.format(Locale.ROOT, "02:00:00:%02X:%02X:%02X",
                (id >> 16) & 0xFF, (id >> 8) & 0xFF, id & 0xFF);
    }

    private static Long parseVirtualZoneId(String mac) {
        if (mac != null && mac.startsWith("02:00:00:")) {
            try {
                String hex = mac.substring(9).replace(":", "");
                return Long.parseLong(hex, 16);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<Snapshot> snapshots(String source) {
        validateSource(source);
        long offlineSeconds = getOfflineTimeoutSeconds();
        Map<String, Snapshot> resultByMac = new LinkedHashMap<>();
        Set<Long> coveredZoneIds = new HashSet<>();

        for (UpstashDevice device : devices.findAll()) {
            String mac = device.getMacAddress().toUpperCase(Locale.ROOT);
            var stored = samples.findFirstByMacAddressOrderByMeasuredAtDescIdDesc(mac);
            String payload = stored.map(s -> s.getPayload()).orElse(null);
            Instant importedAt = stored.map(s -> s.getImportedAt().toInstant(ZoneOffset.UTC)).orElse(null);
            Point point = payload == null ? null : point(mac, payload);
            String ingestSource = stored.map(s -> s.getIngestSource()).orElse(null);

            Zone zone = device.getZone();
            if (zone != null) {
                coveredZoneIds.add(zone.getId());
                Point rawPoint = latestRawPointForZone(zone.getId());
                if (rawPoint != null && (point == null || (rawPoint.measuredAt() != null
                        && (point.measuredAt() == null || rawPoint.measuredAt().isAfter(point.measuredAt()))))) {
                    point = mergePoints(point, rawPoint);
                    if (importedAt == null) importedAt = rawPoint.measuredAt();
                    if (ingestSource == null) ingestSource = "sensor_data_raw";
                }
            }
            resultByMac.put(mac, snapshot(zone, mac, point, importedAt, source, ingestSource, offlineSeconds));
        }

        for (DeviceSet set : deviceSetRepository.findAllByOrderByIdAsc()) {
            if (set.getMacAddress() == null) continue;
            String mac = set.getMacAddress().toUpperCase(Locale.ROOT);
            Zone zone = set.getZone();
            if (zone != null) coveredZoneIds.add(zone.getId());
            if (resultByMac.containsKey(mac)) continue;
            Point rawPoint = zone != null ? latestRawPointForZone(zone.getId()) : null;
            Instant importedAt = rawPoint != null ? rawPoint.measuredAt() : null;
            resultByMac.put(mac, snapshot(zone, mac, rawPoint, importedAt, source,
                    rawPoint != null ? "sensor_data_raw" : "device_set", offlineSeconds));
        }

        for (Zone zone : zoneRepository.findAll()) {
            if (coveredZoneIds.contains(zone.getId())) continue;
            Point rawPoint = latestRawPointForZone(zone.getId());
            if (rawPoint != null) {
                String vMac = virtualMacForZone(zone.getId());
                resultByMac.put(vMac, snapshot(zone, vMac, rawPoint, rawPoint.measuredAt(),
                        source, "sensor_data_raw", offlineSeconds));
            }
        }

        return resultByMac.values().stream()
                .sorted(Comparator.comparing(Snapshot::macAddress))
                .toList();
    }

    private Point latestRawPointForZone(Long zoneId) {
        if (zoneId == null) return null;
        var tempOpt = sensorDataRawRepository.findFirstByMetricSensorZoneIdAndMetricMetricCodeOrderByMeasuredAtDescIdDesc(
                zoneId, MetricCode.TEMPERATURE);
        var humOpt = sensorDataRawRepository.findFirstByMetricSensorZoneIdAndMetricMetricCodeOrderByMeasuredAtDescIdDesc(
                zoneId, MetricCode.HUMIDITY);
        var mq135Opt = sensorDataRawRepository.findFirstByMetricSensorZoneIdAndMetricSensorSensorModelOrderByMeasuredAtDescIdDesc(
                zoneId, SensorModel.MQ135);
        var mq7Opt = sensorDataRawRepository.findFirstByMetricSensorZoneIdAndMetricSensorSensorModelOrderByMeasuredAtDescIdDesc(
                zoneId, SensorModel.MQ7);
        var gasAnyOpt = sensorDataRawRepository.findFirstByMetricSensorZoneIdAndMetricMetricCodeOrderByMeasuredAtDescIdDesc(
                zoneId, MetricCode.GAS);

        Instant latest = null;
        String sampleKey = "raw-latest";
        for (var opt : List.of(tempOpt, humOpt, mq135Opt, mq7Opt, gasAnyOpt)) {
            if (opt.isPresent() && opt.get().getMeasuredAt() != null) {
                Instant ts = opt.get().getMeasuredAt().toInstant(ZoneOffset.UTC);
                if (latest == null || ts.isAfter(latest)) {
                    latest = ts;
                    sampleKey = opt.get().getSampleKey();
                }
            }
        }
        if (latest == null) return null;
        BigDecimal mq135 = mq135Opt.map(SensorDataRaw::getMeasuredValue)
                .orElseGet(() -> gasAnyOpt.map(SensorDataRaw::getMeasuredValue).orElse(null));
        return new Point(
                sampleKey,
                latest,
                tempOpt.map(SensorDataRaw::getMeasuredValue).orElse(null),
                humOpt.map(SensorDataRaw::getMeasuredValue).orElse(null),
                mq135,
                mq7Opt.map(SensorDataRaw::getMeasuredValue).orElse(null)
        );
    }

    private Point mergePoints(Point primary, Point secondary) {
        if (primary == null) return secondary;
        if (secondary == null) return primary;
        return new Point(
                secondary.sampleKey() != null ? secondary.sampleKey() : primary.sampleKey(),
                secondary.measuredAt() != null ? secondary.measuredAt() : primary.measuredAt(),
                secondary.temperatureC() != null ? secondary.temperatureC() : primary.temperatureC(),
                secondary.humidityPercent() != null ? secondary.humidityPercent() : primary.humidityPercent(),
                secondary.mq135Adc() != null ? secondary.mq135Adc() : primary.mq135Adc(),
                secondary.mq7Adc() != null ? secondary.mq7Adc() : primary.mq7Adc()
        );
    }

    @Transactional(readOnly = true)
    public List<Point> history(String mac, String source, int hours, int limit) {
        validateSource(source);
        mac = mac.toUpperCase(Locale.ROOT);
        if (!mac.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}") || hours < 1 || hours > 168 || limit < 1 || limit > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid history parameters");
        }
        var upstashOpt = devices.findById(mac);
        var deviceSetOpt = deviceSetRepository.findByMacAddress(mac);
        Long virtualZoneId = parseVirtualZoneId(mac);
        if (upstashOpt.isEmpty() && deviceSetOpt.isEmpty()
                && (virtualZoneId == null || !zoneRepository.existsById(virtualZoneId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found");
        }

        Instant from = Instant.now().minus(Duration.ofHours(hours));
        LocalDateTime fromLdt = LocalDateTime.ofInstant(from, ZoneOffset.UTC);
        Map<String, Point> pointMap = new LinkedHashMap<>();

        for (var row : samples.findByMacAddressAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDescIdDesc(
                mac, fromLdt, PageRequest.of(0, limit))) {
            Point p = point(mac, row.getPayload());
            pointMap.put(p.sampleKey(), p);
        }

        Long zoneId = upstashOpt.map(d -> d.getZone() != null ? d.getZone().getId() : null)
                .orElseGet(() -> deviceSetOpt.map(ds -> ds.getZone() != null ? ds.getZone().getId() : null)
                        .orElse(virtualZoneId));

        if (zoneId != null && pointMap.size() < limit) {
            List<SensorDataRaw> rawRows = sensorDataRawRepository
                    .findByMetricSensorZoneIdAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDescIdDesc(
                            zoneId, fromLdt, PageRequest.of(0, limit * 4));
            Map<String, List<SensorDataRaw>> grouped = new LinkedHashMap<>();
            for (SensorDataRaw r : rawRows) {
                grouped.computeIfAbsent(r.getSampleKey(), k -> new ArrayList<>()).add(r);
            }
            for (var entry : grouped.entrySet()) {
                if (pointMap.containsKey(entry.getKey())) continue;
                BigDecimal temp = null, hum = null, mq135 = null, mq7 = null;
                Instant measuredAt = null;
                for (SensorDataRaw r : entry.getValue()) {
                    if (measuredAt == null && r.getMeasuredAt() != null) {
                        measuredAt = r.getMeasuredAt().toInstant(ZoneOffset.UTC);
                    }
                    MetricCode code = r.getMetric().getMetricCode();
                    if (code == MetricCode.TEMPERATURE) temp = r.getMeasuredValue();
                    else if (code == MetricCode.HUMIDITY) hum = r.getMeasuredValue();
                    else if (code == MetricCode.GAS) {
                        SensorModel model = r.getMetric().getSensor().getSensorModel();
                        if (model == SensorModel.MQ7) mq7 = r.getMeasuredValue();
                        else mq135 = r.getMeasuredValue();
                    }
                }
                if (measuredAt != null) {
                    pointMap.put(entry.getKey(), new Point(entry.getKey(), measuredAt, temp, hum, mq135, mq7));
                }
                if (pointMap.size() >= limit) break;
            }
        }

        List<Point> points = new ArrayList<>(pointMap.values());
        points.sort(Comparator.comparing(Point::measuredAt).thenComparing(Point::sampleKey));
        return points;
    }

    private Point point(String mac, String payload) {
        UpstashSample sample = mapper.readValue(payload, UpstashSample.class);
        if (!"esp32_direct".equals(sample.source()) || !mac.equalsIgnoreCase(sample.macAddress())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected device data");
        }
        boolean dhtOk = Boolean.TRUE.equals(sample.dhtOk());
        return new Point(sample.sampleKey(), OffsetDateTime.parse(sample.measuredAt()).toInstant(),
                dhtOk ? sample.temperatureC() : null, dhtOk ? sample.humidityPercent() : null,
                sample.mq135Adc(), sample.mq7Adc());
    }

    private Snapshot snapshot(Zone zone, String macAddress, Point point, Instant importedAt,
                              String source, String ingestSource, long offlineSeconds) {
        Instant measuredAt = point == null ? null : point.measuredAt();
        boolean stale = measuredAt == null || measuredAt.isBefore(Instant.now().minusSeconds(offlineSeconds));
        Long zoneId = zone != null ? zone.getId() : null;
        String name = zone != null ? zone.getName() : ("ESP32 " + macAddress);
        Double lat = zone != null ? zone.getLatitude() : null;
        Double lng = zone != null ? zone.getLongitude() : null;
        return new Snapshot(zoneId, macAddress, name, lat, lng,
                point == null ? null : point.temperatureC(), point == null ? null : point.humidityPercent(),
                point == null ? null : point.mq135Adc(), point == null ? null : point.mq7Adc(),
                measuredAt, importedAt, source, ingestSource, stale);
    }

    private void validateSource(String source) {
        if (!"mysql".equals(source)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only MySQL data access is supported");
        }
    }

    public record Point(String sampleKey, Instant measuredAt, BigDecimal temperatureC,
                        BigDecimal humidityPercent, BigDecimal mq135Adc, BigDecimal mq7Adc) {
        public Point(String sampleKey, Instant measuredAt, BigDecimal temperatureC,
                     BigDecimal humidityPercent, BigDecimal mq135Adc) {
            this(sampleKey, measuredAt, temperatureC, humidityPercent, mq135Adc, null);
        }
    }
    public record Snapshot(Long zoneId, String macAddress, String name, Double latitude, Double longitude,
                           BigDecimal temperatureC, BigDecimal humidityPercent, BigDecimal mq135Adc, BigDecimal mq7Adc,
                           Instant measuredAt, Instant importedAt, String source, String ingestSource, boolean stale) {
        public Snapshot(Long zoneId, String macAddress, String name, Double latitude, Double longitude,
                        BigDecimal temperatureC, BigDecimal humidityPercent, BigDecimal mq135Adc,
                        Instant measuredAt, Instant importedAt, String source, String ingestSource, boolean stale) {
            this(zoneId, macAddress, name, latitude, longitude, temperatureC, humidityPercent, mq135Adc, null,
                 measuredAt, importedAt, source, ingestSource, stale);
        }
    }
}
