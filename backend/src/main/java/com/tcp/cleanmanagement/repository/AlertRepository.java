package com.tcp.cleanmanagement.repository;
import com.tcp.cleanmanagement.entity.Alert;
import com.tcp.cleanmanagement.entity.DeviceSetHistory;
import com.tcp.cleanmanagement.enums.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByStatus(AlertStatus status);
    List<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status);
    List<Alert> findAllByOrderByCreatedAtDesc();
    List<Alert> findByStatusAndZoneIdIn(AlertStatus status, List<Long> zoneIds);
    List<Alert> findByZoneId(Long zoneId);
    List<Alert> findBySensorId(Long sensorId);

    @Modifying
    @Query("UPDATE Alert a SET a.deviceSetHistory = :history " +
           "WHERE a.deviceSetHistory IS NULL AND a.zone.id = :zoneId AND a.sensor.id IN " +
           "(SELECT s.id FROM Sensor s WHERE s.deviceSet.id = :setId)")
    int linkUnassignedHistory(@Param("setId") Long deviceSetId,
                              @Param("zoneId") Long zoneId,
                              @Param("history") DeviceSetHistory history);

    @Modifying
    @Query("UPDATE Alert a SET a.deviceSetHistory = NULL WHERE a.deviceSetHistory.deviceSet.id = :setId")
    int unlinkHistoryByDeviceSetId(@Param("setId") Long deviceSetId);

    @Modifying
    @Query("UPDATE Alert a SET a.deviceSetHistory = NULL WHERE a.deviceSetHistory.zone.id = :zoneId")
    int unlinkHistoryByZoneId(@Param("zoneId") Long zoneId);

    @Modifying
    @Query("DELETE FROM Alert a WHERE a.sensor.id = :sensorId")
    int deleteBySensorId(@Param("sensorId") Long sensorId);

    @Modifying
    @Query("DELETE FROM Alert a WHERE a.zone.id = :zoneId")
    int deleteByZoneId(@Param("zoneId") Long zoneId);
}
