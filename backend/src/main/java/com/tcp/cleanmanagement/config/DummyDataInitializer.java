package com.tcp.cleanmanagement.config;

import com.tcp.cleanmanagement.entity.Sensor;
import com.tcp.cleanmanagement.entity.SensorDataRaw;
import com.tcp.cleanmanagement.entity.SensorMetric;
import com.tcp.cleanmanagement.entity.Zone;
import com.tcp.cleanmanagement.enums.SensorModel;
import com.tcp.cleanmanagement.enums.SensorStatus;
import com.tcp.cleanmanagement.enums.MetricCode;
import com.tcp.cleanmanagement.enums.TimeSource;
import com.tcp.cleanmanagement.enums.SensorType;
import com.tcp.cleanmanagement.enums.ZoneType;
import com.tcp.cleanmanagement.repository.SensorDataRawRepository;
import com.tcp.cleanmanagement.repository.SensorRepository;
import com.tcp.cleanmanagement.repository.SensorMetricRepository;
import com.tcp.cleanmanagement.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;

@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class DummyDataInitializer implements CommandLineRunner {

    private final ZoneRepository zoneRepository;
    private final SensorRepository sensorRepository;
    private final SensorDataRawRepository sensorDataRawRepository;
    private final SensorMetricRepository sensorMetricRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Mock / Dummy data generation permanently disabled
    }

    private void saveGasReading(Sensor sensor, String value) {
        SensorMetric metric = sensorMetricRepository.save(SensorMetric.builder()
                .sensor(sensor).metricCode(MetricCode.GAS).unitCode("UNVERIFIED").build());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        sensorDataRawRepository.save(SensorDataRaw.builder()
                .metric(metric).sampleKey("dummy-initial-data-" + sensor.getId())
                .measuredValue(new BigDecimal(value))
                .measuredAt(now).receivedAt(now).timeSource(TimeSource.SERVER).build());
    }

    private void saveTempHumidReading(Sensor sensor, String temp, String humid) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        SensorMetric tempMetric = sensorMetricRepository.save(SensorMetric.builder()
                .sensor(sensor).metricCode(MetricCode.TEMPERATURE).unitCode("CELSIUS").build());
        sensorDataRawRepository.save(SensorDataRaw.builder()
                .metric(tempMetric).sampleKey("dummy-init-temp-" + sensor.getId())
                .measuredValue(new BigDecimal(temp))
                .measuredAt(now).receivedAt(now).timeSource(TimeSource.SERVER).build());

        SensorMetric humidMetric = sensorMetricRepository.save(SensorMetric.builder()
                .sensor(sensor).metricCode(MetricCode.HUMIDITY).unitCode("PERCENT").build());
        sensorDataRawRepository.save(SensorDataRaw.builder()
                .metric(humidMetric).sampleKey("dummy-init-humid-" + sensor.getId())
                .measuredValue(new BigDecimal(humid))
                .measuredAt(now).receivedAt(now).timeSource(TimeSource.SERVER).build());
    }
}
