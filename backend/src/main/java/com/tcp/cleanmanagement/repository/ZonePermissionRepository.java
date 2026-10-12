package com.tcp.cleanmanagement.repository;

import com.tcp.cleanmanagement.entity.ZonePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ZonePermissionRepository extends JpaRepository<ZonePermission, Long> {

    /** 사용자에게 할당된 담당 구역 ID 목록 */
    @Query("select zp.zone.id from ZonePermission zp where zp.user.id = :userId")
    List<Long> findZoneIdsByUserId(@Param("userId") Long userId);
}
