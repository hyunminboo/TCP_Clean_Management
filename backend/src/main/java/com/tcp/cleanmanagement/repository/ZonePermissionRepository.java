package com.tcp.cleanmanagement.repository;

import com.tcp.cleanmanagement.entity.ZonePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ZonePermissionRepository extends JpaRepository<ZonePermission, Long> {
    List<ZonePermission> findByUserId(Long userId);
    List<ZonePermission> findByZoneId(Long zoneId);
    Optional<ZonePermission> findByUserIdAndZoneId(Long userId, Long zoneId);

    @Modifying
    @Query("DELETE FROM ZonePermission zp WHERE zp.zone.id = :zoneId")
    void deleteByZoneId(@Param("zoneId") Long zoneId);

    @Modifying
    @Query("DELETE FROM ZonePermission zp WHERE zp.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
