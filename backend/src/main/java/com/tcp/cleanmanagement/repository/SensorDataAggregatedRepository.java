package com.tcp.cleanmanagement.repository;

import com.tcp.cleanmanagement.entity.SensorDataAggregated;
import com.tcp.cleanmanagement.enums.AggregationGranularity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SensorDataAggregatedRepository extends JpaRepository<SensorDataAggregated, Long> {
    Optional<SensorDataAggregated> findByMetricIdAndGranularityAndBucketStart(
            Long metricId, AggregationGranularity granularity, LocalDateTime bucketStart);

    @Modifying
    @Query("DELETE FROM SensorDataAggregated a WHERE a.metric.sensor.id = :sensorId")
    int deleteBySensorId(@Param("sensorId") Long sensorId);

    @Modifying
    @Query("DELETE FROM SensorDataAggregated a WHERE a.metric.sensor.zone.id = :zoneId")
    int deleteByZoneId(@Param("zoneId") Long zoneId);

    @Modifying
    @Query("DELETE FROM SensorDataAggregated a WHERE a.bucketStart < :cutoffTime")
    int deleteOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);
}
