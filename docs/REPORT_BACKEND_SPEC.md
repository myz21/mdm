# Apple Reports Backend API Specification

> Bu doküman, 5 yeni rapor türü için backend developer'a verilecek teknik API spesifikasyonudur.
> Mevcut `ReportController.java`, `ReportService.java` ve `ReportServiceImpl.java`'ya eklenecek.

---

## Ortak Platform CASE WHEN Bloğu

Tüm raporlarda `apple_device.product_name` üzerinden platform çözümlemesi yapılacak. SQL function olarak tanımlamak önerilir:

```sql
CREATE OR REPLACE FUNCTION resolve_platform(product_name TEXT) RETURNS TEXT AS $$
BEGIN
  RETURN CASE
    WHEN product_name ILIKE '%iPhone%' OR product_name ILIKE '%iPod%' THEN 'iOS'
    WHEN product_name ILIKE '%Mac%' OR product_name ILIKE '%iMac%' OR product_name ILIKE '%MacBook%' THEN 'macOS'
    WHEN product_name ILIKE '%Apple TV%' THEN 'tvOS'
    WHEN product_name ILIKE '%Watch%' THEN 'watchOS'
    WHEN product_name ILIKE '%Reality%' OR product_name ILIKE '%Vision%' THEN 'visionOS'
    ELSE 'unknown'
  END;
END;
$$ LANGUAGE plpgsql IMMUTABLE;
```

Ya da her query'de inline CASE WHEN:
```sql
CASE
  WHEN d.product_name ILIKE '%iPhone%' OR d.product_name ILIKE '%iPod%' THEN 'iOS'
  WHEN d.product_name ILIKE '%Mac%' OR d.product_name ILIKE '%iMac%' OR d.product_name ILIKE '%MacBook%' THEN 'macOS'
  WHEN d.product_name ILIKE '%Apple TV%' THEN 'tvOS'
  WHEN d.product_name ILIKE '%Watch%' THEN 'watchOS'
  WHEN d.product_name ILIKE '%Reality%' OR d.product_name ILIKE '%Vision%' THEN 'visionOS'
  ELSE 'unknown'
END
```

---

## RAPOR 1: Command Analysis

### Flyway Migration: `V16__Add_Command_Report_Indexes.sql`

```sql
CREATE INDEX IF NOT EXISTS idx_apple_command_status ON apple_command (status);
CREATE INDEX IF NOT EXISTS idx_apple_command_type ON apple_command (command_type);
CREATE INDEX IF NOT EXISTS idx_apple_command_request_time ON apple_command (request_time DESC);
CREATE INDEX IF NOT EXISTS idx_apple_command_device_udid ON apple_command (apple_device_udid);
CREATE INDEX IF NOT EXISTS idx_apple_command_policy_id ON apple_command (policy_id);
CREATE INDEX IF NOT EXISTS idx_apple_command_composite
    ON apple_command (status, command_type, request_time DESC);
```

### Endpoint 1: `GET /reports/commands/summary`

Filtre parametrelerine göre komut istatistikleri.

**Query Parameters:**

| Param | Type | Required | Default | Açıklama |
|-------|------|----------|---------|----------|
| dateFrom | String (ISO date) | No | null (tümü) | Başlangıç tarihi |
| dateTo | String (ISO date) | No | null | Bitiş tarihi |
| commandType | String | No | null (tümü) | MDM RequestType (ör: "DeviceInformation") |
| status | String | No | null (tümü) | COMPLETED/FAILED/PENDING/EXECUTING/CANCELED |
| platform | String | No | null (tümü) | iOS/macOS/tvOS/watchOS/visionOS |

**Response: `CommandReportSummaryDto`**
```json
{
  "totalCommands": 4523,
  "completedCommands": 3890,
  "failedCommands": 312,
  "pendingCommands": 45,
  "executingCommands": 12,
  "canceledCommands": 264,
  "avgExecutionTimeMs": 2340,
  "successRate": 86.01
}
```

**SQL Mantığı:**
```sql
SELECT
  COUNT(*) AS total_commands,
  COUNT(*) FILTER (WHERE c.status = 'COMPLETED') AS completed_commands,
  COUNT(*) FILTER (WHERE c.status = 'FAILED') AS failed_commands,
  COUNT(*) FILTER (WHERE c.status = 'PENDING') AS pending_commands,
  COUNT(*) FILTER (WHERE c.status = 'EXECUTING') AS executing_commands,
  COUNT(*) FILTER (WHERE c.status = 'CANCELED') AS canceled_commands,
  AVG(EXTRACT(EPOCH FROM (c.completion_time - c.execution_time)) * 1000)
    FILTER (WHERE c.status = 'COMPLETED' AND c.execution_time IS NOT NULL AND c.completion_time IS NOT NULL)
    AS avg_execution_time_ms
FROM apple_command c
JOIN apple_device d ON d.udid = c.apple_device_udid
WHERE d.status != 'DELETED'
  AND (:dateFrom IS NULL OR c.request_time >= :dateFrom::timestamp)
  AND (:dateTo IS NULL OR c.request_time < :dateTo::timestamp + INTERVAL '1 day')
  AND (:commandType IS NULL OR c.command_type = :commandType)
  AND (:status IS NULL OR c.status = :status)
  AND (:platform IS NULL OR <PLATFORM_CASE_WHEN(d.product_name)> = :platform)
```

`successRate` = `completedCommands * 100.0 / NULLIF(completedCommands + failedCommands, 0)` (Java'da hesaplanacak)

---

### Endpoint 2: `GET /reports/commands/trend`

Tarih aralığına göre günlük komut sayıları.

**Query Parameters:** dateFrom, dateTo, commandType, platform (hepsi opsiyonel)

**Response: `List<CommandDailyTrendDto>`**
```json
[
  { "date": "2026-02-25", "total": 142, "completed": 120, "failed": 15, "canceled": 7 },
  { "date": "2026-02-26", "total": 158, "completed": 140, "failed": 10, "canceled": 8 }
]
```

**SQL Mantığı:**
```sql
SELECT
  CAST(c.request_time AS DATE) AS cmd_date,
  COUNT(*) AS total,
  COUNT(*) FILTER (WHERE c.status = 'COMPLETED') AS completed,
  COUNT(*) FILTER (WHERE c.status = 'FAILED') AS failed,
  COUNT(*) FILTER (WHERE c.status = 'CANCELED') AS canceled
FROM apple_command c
JOIN apple_device d ON d.udid = c.apple_device_udid
WHERE d.status != 'DELETED'
  AND (:dateFrom IS NULL OR c.request_time >= :dateFrom::timestamp)
  AND (:dateTo IS NULL OR c.request_time < :dateTo::timestamp + INTERVAL '1 day')
  AND (:commandType IS NULL OR c.command_type = :commandType)
  AND (:platform IS NULL OR <PLATFORM_CASE_WHEN> = :platform)
GROUP BY CAST(c.request_time AS DATE)
ORDER BY cmd_date ASC
```

---

### Endpoint 3: `GET /reports/commands/types`

Komut türü bazında kırılım.

**Query Parameters:** dateFrom, dateTo, platform (opsiyonel)

**Response: `List<CommandTypeBreakdownDto>`**
```json
[
  {
    "commandType": "DeviceInformation",
    "total": 1200, "completed": 1150, "failed": 30, "canceled": 10, "pending": 10,
    "avgExecutionTimeMs": 1850
  },
  {
    "commandType": "InstallProfile",
    "total": 890, "completed": 820, "failed": 50, "canceled": 15, "pending": 5,
    "avgExecutionTimeMs": 3200
  }
]
```

**SQL Mantığı:**
```sql
SELECT
  c.command_type,
  COUNT(*) AS total,
  COUNT(*) FILTER (WHERE c.status = 'COMPLETED') AS completed,
  COUNT(*) FILTER (WHERE c.status = 'FAILED') AS failed,
  COUNT(*) FILTER (WHERE c.status = 'CANCELED') AS canceled,
  COUNT(*) FILTER (WHERE c.status = 'PENDING') AS pending,
  AVG(EXTRACT(EPOCH FROM (c.completion_time - c.execution_time)) * 1000)
    FILTER (WHERE c.status = 'COMPLETED' AND c.execution_time IS NOT NULL AND c.completion_time IS NOT NULL)
    AS avg_execution_time_ms
FROM apple_command c
JOIN apple_device d ON d.udid = c.apple_device_udid
WHERE d.status != 'DELETED'
  AND (:dateFrom IS NULL OR c.request_time >= :dateFrom::timestamp)
  AND (:dateTo IS NULL OR c.request_time < :dateTo::timestamp + INTERVAL '1 day')
  AND (:platform IS NULL OR <PLATFORM_CASE_WHEN> = :platform)
GROUP BY c.command_type
ORDER BY total DESC
```

---

### Endpoint 4: `POST /reports/commands/list`

Sayfalanmış komut listesi.

**Request Body: `CommandReportRequestDto`**
```json
{
  "dateFrom": "2026-02-01",
  "dateTo": "2026-03-01",
  "commandType": "InstallProfile",
  "status": "FAILED",
  "platform": "iOS",
  "page": 0,
  "size": 25,
  "sortBy": "requestTime",
  "sortDesc": true
}
```

**Response: `PagedModel<CommandReportItemDto>`**
```json
{
  "content": [
    {
      "id": "uuid",
      "commandType": "InstallProfile",
      "status": "FAILED",
      "deviceId": "uuid",
      "serialNumber": "C39X...",
      "productName": "iPhone 16 Pro",
      "platform": "iOS",
      "requestTime": "2026-02-28T14:30:00Z",
      "executionTime": "2026-02-28T14:30:05Z",
      "completionTime": "2026-02-28T14:30:08Z",
      "executionDurationMs": 3000,
      "failureReason": "Profile installation rejected by device",
      "policyId": "uuid-or-null",
      "policyName": "Corporate WiFi Policy"
    }
  ],
  "page": {
    "size": 25,
    "number": 0,
    "totalElements": 312,
    "totalPages": 13
  }
}
```

**SQL Mantığı:**
```sql
SELECT
  c.id, c.command_type, c.status,
  d.id AS device_id, d.serial_number, d.product_name,
  <PLATFORM_CASE_WHEN> AS platform,
  c.request_time, c.execution_time, c.completion_time,
  CASE WHEN c.execution_time IS NOT NULL AND c.completion_time IS NOT NULL
    THEN EXTRACT(EPOCH FROM (c.completion_time - c.execution_time)) * 1000
    ELSE NULL END AS execution_duration_ms,
  c.failure_reason,
  c.policy_id, p.name AS policy_name,
  COUNT(*) OVER() AS total_count
FROM apple_command c
JOIN apple_device d ON d.udid = c.apple_device_udid
LEFT JOIN policy p ON p.id = c.policy_id AND p.status = 'ACTIVE'
WHERE d.status != 'DELETED'
  AND (:dateFrom IS NULL OR c.request_time >= :dateFrom::timestamp)
  AND (:dateTo IS NULL OR c.request_time < :dateTo::timestamp + INTERVAL '1 day')
  AND (:commandType IS NULL OR c.command_type = :commandType)
  AND (:status IS NULL OR c.status = :status)
  AND (:platform IS NULL OR <PLATFORM_CASE_WHEN> = :platform)
ORDER BY <DYNAMIC_SORT>
LIMIT :size OFFSET :offset
```

**Geçerli sortBy değerleri:** `requestTime` (default), `commandType`, `status`, `executionDurationMs`

### Backend Dosya Yapısı

```
apple_mdm/src/main/
├── resources/db/migration/
│   └── V16__Add_Command_Report_Indexes.sql
└── java/.../apple_mdm/
    ├── models/api/report/
    │   ├── CommandReportSummaryDto.java
    │   ├── CommandDailyTrendDto.java
    │   ├── CommandTypeBreakdownDto.java
    │   ├── CommandReportItemDto.java
    │   └── CommandReportRequestDto.java
    ├── repositories/
    │   └── AppleCommandRepository.java          ← 4 yeni native query method
    ├── services/report/
    │   └── ReportService.java                   ← 4 yeni method signature
    ├── managers/report/
    │   └── ReportServiceImpl.java               ← 4 yeni implementation
    └── controllers/
        └── ReportController.java                ← 4 yeni endpoint
```

---

## RAPOR 2: Fleet Health

### Endpoint 1: `GET /reports/fleet-health/summary`

**Query Parameters:** platform (opsiyonel)

**Response: `FleetHealthSummaryDto`**
```json
{
  "totalDevicesWithTelemetry": 245,
  "avgBatteryLevel": 67.3,
  "lowBatteryCount": 12,
  "criticalStorageCount": 8,
  "thermalWarningCount": 3,
  "networkDistribution": {
    "wifi": 180, "cellular": 45, "ethernet": 15, "none": 5
  },
  "batteryDistribution": [
    { "range": "0-10", "count": 3 },
    { "range": "10-20", "count": 5 },
    { "range": "20-30", "count": 8 },
    { "range": "30-40", "count": 12 },
    { "range": "40-50", "count": 18 },
    { "range": "50-60", "count": 25 },
    { "range": "60-70", "count": 35 },
    { "range": "70-80", "count": 40 },
    { "range": "80-90", "count": 54 },
    { "range": "90-100", "count": 45 }
  ],
  "storageDistribution": [
    { "range": "0-10", "count": 8 },
    { "range": "10-20", "count": 10 },
    { "range": "20-30", "count": 15 },
    { "range": "30-40", "count": 20 },
    { "range": "40-50", "count": 25 },
    { "range": "50-60", "count": 30 },
    { "range": "60-70", "count": 35 },
    { "range": "70-80", "count": 40 },
    { "range": "80-90", "count": 35 },
    { "range": "90-100", "count": 27 }
  ]
}
```

**SQL Mantığı:** `agent_telemetry` tablosundan her cihaz için en son telemetri kaydını alıp (window function veya subquery ile `findLatestPerDevice` pattern'i kullanarak) aggregate. Platform filtresi `apple_device.product_name` CASE WHEN ile.

- `lowBatteryCount`: battery_level < 20
- `criticalStorageCount`: storage kullanım oranı > %90
- `thermalWarningCount`: thermal_state IN ('fair', 'serious', 'critical')
- `batteryDistribution`: WIDTH_BUCKET(battery_level, 0, 100, 10) ile histogram
- `storageDistribution`: WIDTH_BUCKET(storage_used_bytes * 100.0 / NULLIF(storage_total_bytes, 0), 0, 100, 10)

> **NOT:** `agent_telemetry` verileri 7 günlük retention'a tabi. Bu rapor sadece en son snapshot'ı gösterir.

---

### Endpoint 2: `POST /reports/fleet-health/devices`

**Request Body: `FleetHealthDeviceRequestDto`**
```json
{
  "platform": "iOS",
  "page": 0,
  "size": 25,
  "sortBy": "batteryLevel",
  "sortDesc": false
}
```

**Response: `PagedModel<FleetHealthDeviceDto>`**
```json
{
  "content": [
    {
      "deviceId": "uuid",
      "serialNumber": "C39X...",
      "productName": "iPhone 16 Pro",
      "platform": "iOS",
      "batteryLevel": 23,
      "batteryCharging": false,
      "batteryState": "unplugged",
      "storageTotalBytes": 256000000000,
      "storageUsedBytes": 230000000000,
      "storageUsagePercent": 89,
      "thermalState": "nominal",
      "networkType": "wifi",
      "wifiSsid": "CorpNet",
      "vpnActive": true,
      "agentLastSeenAt": "2026-03-03T10:15:00Z"
    }
  ],
  "page": { "size": 25, "number": 0, "totalElements": 245, "totalPages": 10 }
}
```

**SQL Mantığı:** `agent_telemetry` en son kayıt JOIN `apple_device`, platform çözümleme, pagination + sort.

**Geçerli sortBy:** `batteryLevel`, `storageUsagePercent`, `thermalState`, `agentLastSeenAt`

### Backend Dosya Yapısı
```
models/api/report/FleetHealthSummaryDto.java
models/api/report/FleetHealthDeviceDto.java
models/api/report/FleetHealthDeviceRequestDto.java
+ ReportService'e 2 method, ReportController'a 2 endpoint
```

---

## RAPOR 3: Compliance

### Endpoint 1: `GET /reports/compliance/summary`

**Query Parameters:** platform (opsiyonel)

**Response: `ComplianceSummaryDto`**
```json
{
  "totalDevices": 300,
  "compliantCount": 265,
  "nonCompliantCount": 25,
  "noPolicyCount": 10,
  "complianceRate": 91.38,
  "topFailureReasons": [
    { "reason": "InstallProfile", "count": 15 },
    { "reason": "DeviceLock", "count": 8 },
    { "reason": "InstallApplication", "count": 5 }
  ]
}
```

**SQL Mantığı:**
- `apple_device` tablosundan `status != 'DELETED'` olanlar
- `is_compliant = true` → compliant
- `is_compliant = false` → non-compliant
- `applied_policy IS NULL` veya `compliance_failures IS NULL AND is_compliant IS NULL` → no policy
- `complianceRate` = `compliantCount * 100.0 / NULLIF(compliantCount + nonCompliantCount, 0)` (Java)
- `topFailureReasons`: `compliance_failures` JSONB alanından aggregate (her cihazın compliance_failures map'ini parse edip command type bazlı sayma)

---

### Endpoint 2: `POST /reports/compliance/devices`

**Request Body: `ComplianceDeviceRequestDto`**
```json
{
  "complianceStatus": "NON_COMPLIANT",
  "platform": "iOS",
  "page": 0,
  "size": 25,
  "sortBy": "productName",
  "sortDesc": false
}
```

`complianceStatus` değerleri: `ALL`, `COMPLIANT`, `NON_COMPLIANT`, `NO_POLICY`

**Response: `PagedModel<ComplianceDeviceDto>`**
```json
{
  "content": [
    {
      "deviceId": "uuid",
      "serialNumber": "C39X...",
      "productName": "iPhone 16 Pro",
      "platform": "iOS",
      "osVersion": "18.5",
      "enrollmentType": "DEP",
      "isCompliant": false,
      "complianceFailures": [
        { "commandType": "InstallProfile", "failureReason": "Profile rejected" },
        { "commandType": "DeviceLock", "failureReason": "Passcode not set" }
      ],
      "appliedPolicyName": "Corporate Security",
      "lastModifiedDate": "2026-03-01T08:00:00Z"
    }
  ],
  "page": { "size": 25, "number": 0, "totalElements": 25, "totalPages": 1 }
}
```

**SQL Mantığı:**
- `apple_device` JOIN `policy` (applied_policy JSONB'den policy ID çıkarılıp JOIN veya direkt isim tutuluyorsa oradan)
- `compliance_failures` JSONB alanı parse edilip DTO'ya map
- Platform CASE WHEN, pagination + sort
- `complianceStatus` filtresi:
  - `ALL` → tüm cihazlar
  - `COMPLIANT` → `is_compliant = true`
  - `NON_COMPLIANT` → `is_compliant = false`
  - `NO_POLICY` → `applied_policy IS NULL`

### Backend Dosya Yapısı
```
models/api/report/ComplianceSummaryDto.java
models/api/report/ComplianceDeviceDto.java
models/api/report/ComplianceDeviceRequestDto.java
+ ReportService'e 2 method, ReportController'a 2 endpoint
```

---

## RAPOR 4: Enrollment

### Endpoint 1: `GET /reports/enrollment/summary`

**Query Parameters:** dateFrom, dateTo, platform (hepsi opsiyonel)

**Response: `EnrollmentSummaryDto`**
```json
{
  "totalActiveDevices": 300,
  "newEnrollments": 45,
  "unenrollments": 12,
  "netChange": 33,
  "typeDistribution": [
    { "enrollmentType": "DEP", "count": 210 },
    { "enrollmentType": "USER_ENROLLMENT", "count": 65 },
    { "enrollmentType": "PROFILE", "count": 20 },
    { "enrollmentType": "UNKNOWN", "count": 5 }
  ]
}
```

**SQL Mantığı:**
- `totalActiveDevices`: `apple_device WHERE status != 'DELETED'`
- `newEnrollments`: `enrollment_history` tablosundan tarih aralığında `enrolled_at` sayımı
- `unenrollments`: `enrollment_history` tablosundan tarih aralığında `unenrolled_at` sayımı
- `netChange` = `newEnrollments - unenrollments` (Java)
- `typeDistribution`: `apple_device WHERE status != 'DELETED' GROUP BY enrollment_type`

---

### Endpoint 2: `GET /reports/enrollment/trend`

**Query Parameters:** dateFrom, dateTo, platform (opsiyonel)

**Response: `List<EnrollmentDailyTrendDto>`**
```json
[
  { "date": "2026-02-25", "enrollments": 5, "unenrollments": 1 },
  { "date": "2026-02-26", "enrollments": 3, "unenrollments": 2 }
]
```

**SQL Mantığı:** `enrollment_history` tablosundan günlük gruplama. İki ayrı GROUP BY + FULL OUTER JOIN veya UNION ile birleştirme.

---

### Endpoint 3: `POST /reports/enrollment/history`

**Request Body: `EnrollmentHistoryRequestDto`**
```json
{
  "dateFrom": "2026-01-01",
  "dateTo": "2026-03-01",
  "enrollmentType": "DEP",
  "platform": "iOS",
  "page": 0,
  "size": 25,
  "sortBy": "enrolledAt",
  "sortDesc": true
}
```

**Response: `PagedModel<EnrollmentHistoryItemDto>`**
```json
{
  "content": [
    {
      "id": "uuid",
      "deviceId": "uuid",
      "serialNumber": "C39X...",
      "productName": "iPhone 16 Pro",
      "platform": "iOS",
      "osVersion": "18.5",
      "enrollmentType": "DEP",
      "status": "ACTIVE",
      "enrolledAt": "2026-02-15T09:30:00",
      "unenrolledAt": null,
      "unenrollReason": null
    }
  ],
  "page": { "size": 25, "number": 0, "totalElements": 300, "totalPages": 12 }
}
```

### Backend Dosya Yapısı
```
models/api/report/EnrollmentSummaryDto.java
models/api/report/EnrollmentDailyTrendDto.java
models/api/report/EnrollmentHistoryItemDto.java
models/api/report/EnrollmentHistoryRequestDto.java
+ EnrollmentHistoryRepository'ye yeni native query methods
+ ReportService'e 3 method, ReportController'a 3 endpoint
```

---

## RAPOR 5: Security Posture

### Endpoint 1: `GET /reports/security/summary`

**Query Parameters:** platform (opsiyonel)

**Response: `SecuritySummaryDto`**
```json
{
  "totalDevices": 300,
  "supervisedCount": 250,
  "activationLockCount": 270,
  "cloudBackupCount": 220,
  "findMyCount": 280,
  "jailbreakCount": 2,
  "vpnActiveCount": 85,
  "passcodeCompliantCount": 290,
  "appAnalyticsCount": 195,
  "diagnosticSubmissionCount": 180
}
```

**SQL Mantığı:**
- `apple_device_information` tablosundan: `supervised`, `activation_lock_enabled`, `cloud_backup_enabled`, `device_locator_service_enabled` (Find My) sayımları — mevcut `findFeatureEnablementCounts()` pattern'i genişletilecek
- `agent_telemetry` en son kayıtlardan: `jailbreak_detected = true`, `vpn_active = true` sayımları
- `security_info` JSONB alanından: passcode compliance bilgisi (varsa)
- Platform filtresi: apple_device JOIN

---

### Endpoint 2: `POST /reports/security/devices`

**Request Body: `SecurityDeviceRequestDto`**
```json
{
  "platform": "iOS",
  "filter": "JAILBREAK",
  "page": 0,
  "size": 25,
  "sortBy": "productName",
  "sortDesc": false
}
```

`filter` değerleri: `ALL`, `SUPERVISED`, `NOT_SUPERVISED`, `ACTIVATION_LOCK`, `NO_ACTIVATION_LOCK`, `JAILBREAK`, `NO_CLOUD_BACKUP`

**Response: `PagedModel<SecurityDeviceDto>`**
```json
{
  "content": [
    {
      "deviceId": "uuid",
      "serialNumber": "C39X...",
      "productName": "iPhone 16 Pro",
      "platform": "iOS",
      "osVersion": "18.5",
      "supervised": true,
      "activationLockEnabled": true,
      "cloudBackupEnabled": true,
      "findMyEnabled": true,
      "jailbreakDetected": false,
      "vpnActive": false,
      "passcodeCompliant": true
    }
  ],
  "page": { "size": 25, "number": 0, "totalElements": 300, "totalPages": 12 }
}
```

**SQL Mantığı:**
- `apple_device` JOIN `apple_device_information` JOIN (en son `agent_telemetry` subquery)
- filter parametresine göre WHERE koşulu ekleme
- Platform CASE WHEN, pagination + sort

### Backend Dosya Yapısı
```
models/api/report/SecuritySummaryDto.java
models/api/report/SecurityDeviceDto.java
models/api/report/SecurityDeviceRequestDto.java
+ AppleDeviceInformationRepository'ye yeni native query methods
+ ReportService'e 2 method, ReportController'a 2 endpoint
```

---

## Toplam Özet

| Rapor | Endpoint Sayısı | DTO Sayısı | Repository Method |
|-------|----------------|------------|-------------------|
| Command Analysis | 4 | 5 | 4 (AppleCommandRepository) |
| Fleet Health | 2 | 3 | 2 (AgentTelemetryRepository veya yeni) |
| Compliance | 2 | 3 | 2 (AppleDeviceRepository) |
| Enrollment | 3 | 4 | 3 (EnrollmentHistoryRepository) |
| Security Posture | 2 | 3 | 2 (AppleDeviceInformationRepository + AgentTelemetryRepository) |
| **TOPLAM** | **13** | **18** | **13** |

Mevcut `ReportController.java`'ya tüm endpointler eklenecek. Mevcut `ReportService.java` interface'ine ve `ReportServiceImpl.java`'ya tüm methodlar eklenecek.
