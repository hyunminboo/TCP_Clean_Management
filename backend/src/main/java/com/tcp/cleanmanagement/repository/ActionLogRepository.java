package com.tcp.cleanmanagement.repository;

import com.tcp.cleanmanagement.entity.ActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActionLogRepository extends JpaRepository<ActionLog, Long> {
    List<ActionLog> findAllByOrderByCreatedAtDesc();
    List<ActionLog> findByAlertIdOrderByCreatedAtDesc(Long alertId);

    @Modifying
    @Query("DELETE FROM ActionLog l WHERE l.alert.zone.id = :zoneId")
    int deleteByZoneId(@Param("zoneId") Long zoneId);

    @Modifying
    @Query("DELETE FROM ActionLog l WHERE l.alert.sensor.id = :sensorId")
    int deleteBySensorId(@Param("sensorId") Long sensorId);

    @Modifying
    @Query("DELETE FROM ActionLog l WHERE l.admin.id = :adminId")
    int deleteByAdminId(@Param("adminId") Long adminId);
}
