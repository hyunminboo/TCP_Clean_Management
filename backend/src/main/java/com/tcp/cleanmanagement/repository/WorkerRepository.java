package com.tcp.cleanmanagement.repository;

import com.tcp.cleanmanagement.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    List<Worker> findByStatus(String status);
    List<Worker> findByAssignedZoneId(Long assignedZoneId);
    Optional<Worker> findFirstByAssignedZoneIdOrderByIdAsc(Long assignedZoneId);
    Optional<Worker> findFirstByNameAndPhoneOrderByIdAsc(String name, String phone);
}
