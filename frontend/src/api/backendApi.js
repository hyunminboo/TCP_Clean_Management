const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

export const getAuthHeaders = () => {
  const token = localStorage.getItem('tcp_auth_token') || localStorage.getItem('token') || 'admin-token';
  const bearer = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
  return {
    'Content-Type': 'application/json',
    'Authorization': bearer,
    'ngrok-skip-browser-warning': 'true'
  };
};

// 두 좌표 간 거리 계산 함수 (미터 단위)
function getDistanceInMeters(lat1, lon1, lat2, lon2) {
  if (!lat1 || !lon1 || !lat2 || !lon2) return null;
  const R = 6371e3;
  const phi1 = (lat1 * Math.PI) / 180;
  const phi2 = (lat2 * Math.PI) / 180;
  const deltaPhi = ((lat2 - lat1) * Math.PI) / 180;
  const deltaLambda = ((lon2 - lon1) * Math.PI) / 180;

  const a =
    Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
    Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return Math.round(R * c);
}

/**
 * 백엔드 서버(Spring Boot / ngrok)로부터 실제 IoT 구역 목록 및 최신 센서 데이터를 가져옵니다.
 * @param {'restroom' | 'smoking'} category 카테고리
 * @param {number|null} userLat 사용자 현재 위도 (거리 계산용)
 * @param {number|null} userLon 사용자 현재 경도 (거리 계산용)
 */
export const fetchBackendZones = async (category, userLat = null, userLon = null) => {
  try {
    const zoneType = category === 'all' ? '' : category === 'restroom' ? 'TOILET' : 'SMOKING';
    const response = await fetch(`${BASE_URL}/api/public/zones${zoneType ? `?type=${zoneType}` : ''}`, {
      headers: {
        'ngrok-skip-browser-warning': 'true'
      }
    });

    if (!response.ok) {
      throw new Error(`백엔드 통신 오류: HTTP ${response.status}`);
    }

    const data = await response.json();

    return data.filter(item => Number.isFinite(item.latitude) && Number.isFinite(item.longitude)).map((item) => {
      const distance = userLat && userLon ? getDistanceInMeters(userLat, userLon, item.latitude, item.longitude) : null;
      return {
        id: `iot-${item.zoneId}`,
        name: `[IoT] ${item.name}`,
        lat: item.latitude,
        lon: item.longitude,
        address: item.name,
        distance: distance,
        type: category,
        temp: item.temp,
        humidity: item.humidity,
        gas: item.gasLevel != null ? `${item.gasLevel} ADC` : '측정 없음',
        mq7: item.mq7Level != null ? `${item.mq7Level} ADC` : null,
        airQuality: '보정 전 센서값',
        status: item.status || '측정 없음',
        color: item.color || (category === 'restroom' ? '#10b981' : '#3b82f6'),
        isOurSolution: item.isOurSolution ?? true
      };
    });
  } catch (error) {
    console.warn('[backendApi] 백엔드 데이터를 가져오지 못했습니다. 카카오맵 기본 검색 결과를 사용합니다:', error);
    return [];
  }
};

/**
 * 백엔드 MySQL DB에 등록된 전체 관제 구역(Zone) 목록을 조회합니다.
 */
export const fetchAllPublicZones = async (signal) => {
  const response = await fetch(`${BASE_URL}/api/public/zones`, {
    signal,
    headers: {
      'ngrok-skip-browser-warning': 'true'
    }
  });

  if (!response.ok) {
    throw new Error(`구역 목록 조회 실패 (HTTP ${response.status})`);
  }

  return response.json();
};

/**
 * 백엔드 MySQL DB에 신규 화장실/관제구역을 등록합니다.
 */
export const createBackendZone = async (zoneData) => {
  const payload = {
    name: zoneData.name,
    address: zoneData.address || `${zoneData.floor || '1F'} ${zoneData.gender || '공용'} 화장실`,
    latitude: zoneData.lat ?? 37.5665,
    longitude: zoneData.lng ?? 126.9780,
    zoneType: zoneData.zoneType || 'TOILET',
    isOurSolution: true,
    pleasantThreshold: zoneData.pleasantThreshold ?? 300.0,
    needsVentilationThreshold: zoneData.needsVentilationThreshold ?? 600.0
  };

  const response = await fetch(`${BASE_URL}/api/dev/zones`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'ngrok-skip-browser-warning': 'true'
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`구역 등록 실패 (HTTP ${response.status}): ${errorText}`);
  }

  const zoneId = await response.json();

  // 기기 ID (MAC 주소 등)가 있으면 Device Set 등록도 시도
  if (zoneData.deviceId && zoneData.deviceId.trim()) {
    try {
      const rawMac = zoneData.deviceId.trim();
      const macPattern = /^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$/;
      if (macPattern.test(rawMac)) {
        const formattedMac = rawMac.toUpperCase().replace(/-/g, ':');
        await fetch(`${BASE_URL}/api/admin/device-sets`, {
          method: 'POST',
          headers: getAuthHeaders(),
          body: JSON.stringify({
            setCode: `SET-${zoneId}`,
            macAddress: formattedMac,
            zoneId: zoneId,
            installationLabel: zoneData.name
          })
        });
      }
    } catch (e) {
      console.warn('[backendApi] 기기 세트 등록 부가 처리 경고:', e);
    }
  }

  return zoneId;
};

/**
 * 백엔드 MySQL DB에서 구역을 삭제합니다.
 */
export const deleteBackendZone = async (zoneId) => {
  const response = await fetch(`${BASE_URL}/api/dev/zones/${zoneId}`, {
    method: 'DELETE',
    headers: {
      'ngrok-skip-browser-warning': 'true'
    }
  });

  if (!response.ok && response.status !== 404) {
    throw new Error(`구역 삭제 실패 (HTTP ${response.status})`);
  }

  return true;
};

// 청소 작업 이력 로컬 영구 저장소 키
const STORAGE_KEY_CLEANING_LOGS = 'tcp_clean_logs_v1';

export const loadStoredCleaningLogs = (fallbackLogs = []) => {
  try {
    const saved = localStorage.getItem(STORAGE_KEY_CLEANING_LOGS);
    if (saved) {
      const parsed = JSON.parse(saved);
      if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    }
  } catch (e) {
    console.warn('[backendApi] 청소 이력 로드 실패:', e);
  }
  return fallbackLogs;
};

export const persistCleaningLog = (newLog, currentLogs = []) => {
  try {
    const updated = [newLog, ...currentLogs.filter(l => l.id !== newLog.id)];
    localStorage.setItem(STORAGE_KEY_CLEANING_LOGS, JSON.stringify(updated));
    return updated;
  } catch (e) {
    console.warn('[backendApi] 청소 이력 저장 실패:', e);
    return [newLog, ...currentLogs];
  }
};

/**
 * 화장실 구역 활성화/비활성화 토글
 */
export const toggleZoneActiveApi = async (zoneId) => {
  const response = await fetch(`${BASE_URL}/api/dev/zones/${zoneId}/toggle-active`, {
    method: 'PATCH',
    headers: { 'ngrok-skip-browser-warning': 'true' }
  });
  if (!response.ok) throw new Error(`상태 변경 실패 (${response.status})`);
  return response.json();
};

/**
 * 구역 임계값 업데이트
 */
export const updateZoneThresholdsApi = async (zoneId, thresholds) => {
  const response = await fetch(`${BASE_URL}/api/dev/zones/${zoneId}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'ngrok-skip-browser-warning': 'true'
    },
    body: JSON.stringify(thresholds)
  });
  if (!response.ok) throw new Error(`임계값 저장 실패 (${response.status})`);
  return response.json();
};

/**
 * 백엔드 MySQL DB에 센서 단독 등록 (POST /api/dev/sensors)
 */
export const createBackendSensor = async (sensorData) => {
  const response = await fetch(`${BASE_URL}/api/dev/sensors`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'ngrok-skip-browser-warning': 'true'
    },
    body: JSON.stringify(sensorData)
  });
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`센서 등록 실패 (HTTP ${response.status}): ${errorText}`);
  }
  return response.json();
};

/**
 * 미해결 알림 목록 조회
 */
export const fetchAdminAlertsApi = async () => {
  try {
    const response = await fetch(`${BASE_URL}/api/admin/alerts`, {
      headers: getAuthHeaders()
    });
    if (!response.ok) return [];
    return response.json();
  } catch (e) {
    return [];
  }
};

/**
 * 알림 단건 해결 액션 기록
 */
export const resolveAlertApi = async (alertId, actionDetail = '현장 조치 및 점검 완료', adminId = 1) => {
  const response = await fetch(`${BASE_URL}/api/admin/alerts/${alertId}/actions`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ actionDetail, adminId })
  });
  if (!response.ok) throw new Error(`알림 처리 실패 (${response.status})`);
  return true;
};

/**
 * 알림 일괄 해결 액션
 */
export const resolveAllAlertsApi = async (actionDetail = '관리자 일괄 확인 처리', adminId = 1) => {
  const response = await fetch(`${BASE_URL}/api/admin/alerts/batch-actions`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ actionDetail, adminId })
  });
  if (!response.ok) throw new Error(`일괄 처리 실패 (${response.status})`);
  return true;
};

/**
 * 청소 이력 DB 조회
 */
export const fetchCleaningRecordsApi = async (zoneId = null) => {
  try {
    const url = `${BASE_URL}/api/admin/cleaning-records${zoneId ? `?zoneId=${zoneId}` : ''}`;
    const response = await fetch(url, {
      headers: getAuthHeaders()
    });
    if (!response.ok) return [];
    return response.json();
  } catch (e) {
    return [];
  }
};

/**
 * 청소 이력 DB 저장
 */
export const createCleaningRecordApi = async (record) => {
  const response = await fetch(`${BASE_URL}/api/admin/cleaning-records`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify(record)
  });
  if (!response.ok) throw new Error(`청소 기록 저장 실패 (${response.status})`);
  return response.json();
};

/**
 * 관리자용 기기 세트 상세 목록 조회
 */
export const fetchDeviceSetsApi = async () => {
  try {
    const response = await fetch(`${BASE_URL}/api/admin/device-sets`, {
      headers: getAuthHeaders()
    });
    if (!response.ok) return [];
    return response.json();
  } catch (e) {
    return [];
  }
};

/**
 * 기기 세트 이전/수정 히스토리 조회
 */
export const fetchDeviceSetHistoryApi = async (deviceSetId) => {
  try {
    const response = await fetch(`${BASE_URL}/api/admin/device-sets/${deviceSetId}/history`, {
      headers: getAuthHeaders()
    });
    if (!response.ok) return [];
    return response.json();
  } catch (e) {
    return [];
  }
};

/**
 * Upstash 클라우드 동기화 상태 모니터링 조회
 */
export const fetchUpstashStatusApi = async () => {
  try {
    const response = await fetch(`${BASE_URL}/api/iot/upstash/status`, {
      headers: {
        'ngrok-skip-browser-warning': 'true'
      }
    });
    if (!response.ok) return null;
    return response.json();
  } catch (e) {
    return null;
  }
};

/**
 * 백엔드 JWT/세션 로그인 API
 */
export const loginApi = async (username, password) => {
  const response = await fetch(`${BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'ngrok-skip-browser-warning': 'true'
    },
    body: JSON.stringify({ username, password })
  });
  if (!response.ok) {
    const err = await response.text();
    throw new Error(err || '로그인 실패');
  }
  return response.json();
};

