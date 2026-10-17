package com.tcp.cleanmanagement.repository;

import com.tcp.cleanmanagement.entity.UpstashDevice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UpstashDeviceRepository extends JpaRepository<UpstashDevice, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM UpstashDevice d WHERE d.macAddress = :mac")
    Optional<UpstashDevice> findLockedByMac(@Param("mac") String mac);

    List<UpstashDevice> findByZoneId(Long zoneId);

    @Modifying
    @Query("UPDATE UpstashDevice d SET d.temperatureHumiditySensor = NULL WHERE d.temperatureHumiditySensor.id = :sensorId")
    int unlinkTempHumSensor(@Param("sensorId") Long sensorId);

    @Modifying
    @Query("UPDATE UpstashDevice d SET d.gasSensor = NULL WHERE d.gasSensor.id = :sensorId")
    int unlinkGasSensor(@Param("sensorId") Long sensorId);

    @Modifying
    @Query("DELETE FROM UpstashDevice d WHERE d.zone.id = :zoneId")
    int deleteByZoneId(@Param("zoneId") Long zoneId);
}
