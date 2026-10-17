package com.tcp.cleanmanagement.repository;

import com.tcp.cleanmanagement.entity.SensorMetric;
import com.tcp.cleanmanagement.enums.MetricCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SensorMetricRepository extends JpaRepository<SensorMetric, Long> {
    Optional<SensorMetric> findBySensorIdAndMetricCode(Long sensorId, MetricCode metricCode);
    List<SensorMetric> findBySensorId(Long sensorId);

    @Modifying
    @Query("DELETE FROM SensorMetric m WHERE m.sensor.id = :sensorId")
    int deleteBySensorId(@Param("sensorId") Long sensorId);

    @Modifying
    @Query("DELETE FROM SensorMetric m WHERE m.sensor.zone.id = :zoneId")
    int deleteByZoneId(@Param("zoneId") Long zoneId);
}
