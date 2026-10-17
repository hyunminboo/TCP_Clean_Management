package com.tcp.cleanmanagement.repository;

import com.tcp.cleanmanagement.entity.UpstashImportedSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;

public interface UpstashImportedSampleRepository extends JpaRepository<UpstashImportedSample, Long> {
    Optional<UpstashImportedSample> findByMacAddressAndSampleKey(String macAddress, String sampleKey);
    Optional<UpstashImportedSample> findFirstByMacAddressOrderByMeasuredAtDescIdDesc(String macAddress);
    List<UpstashImportedSample> findByMacAddressAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDescIdDesc(
            String macAddress, LocalDateTime from, Pageable pageable);

    @Modifying
    @Query("DELETE FROM UpstashImportedSample s WHERE s.macAddress = :mac")
    int deleteByMacAddress(@Param("mac") String macAddress);

    @Modifying
    @Query("DELETE FROM UpstashImportedSample s WHERE s.measuredAt < :cutoffTime")
    int deleteOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);
}
