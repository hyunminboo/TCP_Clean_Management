package com.tcp.cleanmanagement.repository;

import com.tcp.cleanmanagement.entity.DeviceSetHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceSetHistoryRepository extends JpaRepository<DeviceSetHistory, Long> {
    Optional<DeviceSetHistory> findFirstByDeviceSetIdOrderByIdDesc(Long deviceSetId);
    List<DeviceSetHistory> findByDeviceSetIdOrderByIdDesc(Long deviceSetId);

    @Modifying
    @Query("DELETE FROM DeviceSetHistory h WHERE h.deviceSet.id = :setId")
    int deleteByDeviceSetId(@Param("setId") Long deviceSetId);

    @Modifying
    @Query("DELETE FROM DeviceSetHistory h WHERE h.zone.id = :zoneId")
    int deleteByZoneId(@Param("zoneId") Long zoneId);
}
