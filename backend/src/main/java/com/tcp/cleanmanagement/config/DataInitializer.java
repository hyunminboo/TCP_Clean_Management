package com.tcp.cleanmanagement.config;

import com.tcp.cleanmanagement.entity.User;
import com.tcp.cleanmanagement.entity.Zone;
import com.tcp.cleanmanagement.entity.ZonePermission;
import com.tcp.cleanmanagement.enums.Role;
import com.tcp.cleanmanagement.enums.ZoneType;
import com.tcp.cleanmanagement.repository.UserRepository;
import com.tcp.cleanmanagement.repository.ZonePermissionRepository;
import com.tcp.cleanmanagement.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final ZonePermissionRepository zonePermissionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        // 1. 초기 구역(화장실) 시드
        if (zoneRepository.count() == 0) {
            log.info("초기 화장실 구역(Zone) 시드 데이터를 생성합니다.");
            List<Zone> initialZones = List.of(
                    createZone("1F 남자 화장실", 37.5668, 126.9785),
                    createZone("1F 여자 화장실", 37.5668, 126.9788),
                    createZone("2F 남자 화장실", 37.5670, 126.9783),
                    createZone("2F 여자 화장실", 37.5670, 126.9786),
                    createZone("3F 남자 화장실", 37.5672, 126.9782),
                    createZone("3F 여자 화장실", 37.5672, 126.9785),
                    createZone("B1F 공용 화장실", 37.5664, 126.9780),
                    createZone("옥상 야외 화장실", 37.5679, 126.9781)
            );
            zoneRepository.saveAll(initialZones);
        }

        // 2. 초기 테스트 사용자 계정 시드
        if (userRepository.count() == 0) {
            log.info("초기 테스트 사용자(developer, admin, cleaner)를 생성합니다.");
            String defaultPassword = passwordEncoder.encode("1234");

            // 개발자 (전체 시스템 및 전체 구역 제어 권한)
            User developer = User.builder()
                    .username("developer")
                    .password(defaultPassword)
                    .role(Role.DEVELOPER)
                    .build();
            userRepository.save(developer);

            // 현장 관리자 (1F, 2F 화장실 담당 권한)
            User admin = User.builder()
                    .username("admin")
                    .password(defaultPassword)
                    .role(Role.ADMIN)
                    .build();
            userRepository.save(admin);

            // 청소 담당자 (1F 화장실 담당 권한)
            User cleaner = User.builder()
                    .username("cleaner")
                    .password(defaultPassword)
                    .role(Role.CLEANER)
                    .build();
            userRepository.save(cleaner);

            // 담당 구역 권한 할당
            List<Zone> allZones = zoneRepository.findAll();
            if (allZones.size() >= 4) {
                // admin 에게 1~4번 구역 할당
                for (int i = 0; i < 4; i++) {
                    zonePermissionRepository.save(ZonePermission.builder()
                            .user(admin)
                            .zone(allZones.get(i))
                            .build());
                }
                // cleaner 에게 1~2번 구역 할당
                for (int i = 0; i < 2; i++) {
                    zonePermissionRepository.save(ZonePermission.builder()
                            .user(cleaner)
                            .zone(allZones.get(i))
                            .build());
                }
            }
            log.info("초기 사용자 및 구역 권한 매핑 완료 (비밀번호: 1234)");
        }
    }

    private Zone createZone(String name, double lat, double lon) {
        return Zone.builder()
                .name(name)
                .address("서울특별시 중구 세종대로 110")
                .latitude(lat)
                .longitude(lon)
                .zoneType(ZoneType.TOILET)
                .isOurSolution(true)
                .pleasantThreshold(300f)
                .needsVentilationThreshold(700f)
                .build();
    }
}
