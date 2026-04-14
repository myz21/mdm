# Agent Architecture — MQTT-Based Device Agent System

> Bu dokuman agent sisteminin mimari referansıdır. Gelistirme surecinde bu dokumana uyulacak, degisiklik olursa guncellenecektir.

---

## 1. Genel Bakis

Apple MDM protokolunun desteklemedigi yetenekleri (ekran paylasimi, gercek zamanli konum, heartbeat vb.) cihaz uzerinde calisan bir agent uygulamasi ve MQTT protokolu uzerinden saglayan sistem.

- **iOS Agent** ve **macOS Agent** ayri uygulamalar olarak gelistirilir.
- **Server tarafi** (apple_mdm modulu) tek bir yapida her iki platformu destekler.

---

## 2. Agent Capabilities

| # | Capability | iOS | macOS | Aciklama |
|---|-----------|-----|-------|----------|
| 1 | Heartbeat / Presence | Yes | Yes | Online/offline durumu, MQTT LWT ile otomatik |
| 2 | Konum | Yes | Yes | Periyodik + on-demand konum raporu |
| 3 | Telemetri | Yes | Yes | Batarya, depolama, network bilgisi |
| 4 | App Blocking | No | Yes | blockedAppBundleIDs / allowList enforcement |
| 5 | Compliance Check | Yes (kisitli) | Yes | Jailbreak detection, guvenlik durumu |
| 6 | Geofencing | Yes | Yes | Server-defined geofence, giris/cikis event |
| 7 | Remote Script | No | Yes | Shell komut calistirma |
| 8 | Ekran Paylasimi | No | Yes (Faz 2) | ScreenCaptureKit + WebRTC/relay |

### Platform Farklari

| Ozellik | macOS | iOS/iPadOS |
|---------|-------|------------|
| MQTT baglantisi | LaunchDaemon — her zaman acik | Background mode kisitli, APNS ile wake gerekir |
| Ekran paylasimi | ScreenCaptureKit — tam erisim | ReplayKit — kullanici onayi sart |
| Script execution | Tam shell erisimi | Sandbox — mumkun degil |
| Dosya yonetimi | Full filesystem | Sadece sandbox |
| Konum | Sinirsiz | Background location izni gerekli |

---

## 3. MQTT Topic Yapisi

```
arcops/
  devices/
    {udid}/
      status              <-- Device publish (retained) + LWT
      telemetry           <-- Device publish (periyodik)
      location            <-- Device publish (periyodik + on-demand)
      events              <-- Device publish (geofence, compliance)
      commands            <-- Server publish --> Device subscribe
      responses           <-- Device publish --> Server subscribe
```

---

## 4. Mesaj Formatlari

### 4.1 status — QoS 1, Retained

```json
{
  "online": true,
  "platform": "macOS",
  "agentVersion": "1.0.0",
  "timestamp": "2026-02-16T12:00:00Z"
}
```

**LWT (Last Will and Testament):**
```json
{
  "online": false,
  "timestamp": "2026-02-16T12:05:00Z"
}
```

Cihaz baglantisi koptugunda broker otomatik olarak bu mesaji yayinlar.

### 4.2 telemetry — QoS 0, her 60 saniye (configurable)

```json
{
  "batteryLevel": 85,
  "batteryCharging": true,
  "storageTotal": 256000,
  "storageFree": 98000,
  "networkType": "wifi",
  "wifiSSID": "Office-5G",
  "ipAddress": "192.168.1.42",
  "timestamp": "2026-02-16T12:00:00Z"
}
```

### 4.3 location — QoS 1, periyodik (configurable) + on-demand

```json
{
  "latitude": 41.0082,
  "longitude": 28.9784,
  "accuracy": 12.5,
  "altitude": 45.0,
  "speed": 0.0,
  "timestamp": "2026-02-16T12:00:00Z"
}
```

### 4.4 events — QoS 1

```json
{
  "eventType": "geofence_exit",
  "data": {
    "fenceId": "office-zone",
    "fenceName": "Ofis"
  },
  "timestamp": "2026-02-16T12:00:00Z"
}
```

Event tipleri: `geofence_enter`, `geofence_exit`, `compliance_violation`, `app_blocked`

### 4.5 commands — QoS 2 (exactly once)

```json
{
  "commandId": "uuid",
  "type": "request_location",
  "payload": {},
  "timestamp": "2026-02-16T12:00:00Z"
}
```

### 4.6 responses — QoS 1

```json
{
  "commandId": "uuid",
  "status": "success",
  "data": {},
  "timestamp": "2026-02-16T12:00:00Z"
}
```

---

## 5. Server Tarafi (apple_mdm modulu)

### 5.1 Server SUBSCRIBES (dinler)

| Topic Pattern | Servis | Islev |
|---|---|---|
| `arcops/devices/+/status` | `AgentPresenceService` | Online/offline tracking, DB'ye yaz |
| `arcops/devices/+/telemetry` | `AgentTelemetryService` | Telemetri verisi isle, DB'ye yaz |
| `arcops/devices/+/location` | `AgentLocationService` | Konum kaydet, geofence kontrolu yap |
| `arcops/devices/+/events` | `AgentEventService` | Device event'lerini isle, notification tetikle |
| `arcops/devices/+/responses` | `AgentCommandResponseHandler` | Komut yanitlarini eslestir, durumu guncelle |

### 5.2 Server PUBLISHES (gonderir)

| Topic | Servis | Islev |
|---|---|---|
| `arcops/devices/{udid}/commands` | `AgentCommandService` | Cihaza komut gonder |

### 5.3 Komut Tipleri (server --> device)

| Command Type | Platform | Aciklama |
|---|---|---|
| `request_location` | iOS + macOS | Anlik konum iste |
| `update_config` | iOS + macOS | Agent config guncelle (telemetri interval, geofence tanimlari) |
| `update_app_policy` | macOS | App blocking/allow list guncelle |
| `execute_script` | macOS | Shell script calistir |
| `start_screen_share` | macOS (Faz 2) | Ekran paylasimi baslat |
| `check_compliance` | iOS + macOS | Anlik compliance raporu iste |

---

## 6. Device Tarafi

### 6.1 Device SUBSCRIBES (dinler)

| Topic | Islev |
|---|---|
| `arcops/devices/{kendi-udid}/commands` | Server'dan gelen komutlari al ve calistir |

### 6.2 Device PUBLISHES (gonderir)

| Topic | Tetikleyici | Islev |
|---|---|---|
| `status` | Connect + LWT | Online/offline bildir |
| `telemetry` | Her 60s (configurable) | Sistem bilgisi gonder |
| `location` | Her 5min (configurable) + on-demand | Konum gonder |
| `events` | Geofence/compliance trigger | Event bildir |
| `responses` | Komut sonrasi | Komut yaniti gonder |

---

## 7. Gelistirilecek Maddeler

### 7.1 Server Side (apple_mdm modulu)

1. **MQTT dependency + config** — Eclipse Paho veya HiveMQ client, `application.yaml` MQTT broker ayarlari
2. **AgentMqttConfig** — MQTT client bean, connection, reconnect, wildcard subscription'lar
3. **AgentPresenceService** — `+/status` subscriber, cihaz online/offline DB tracking
4. **AgentTelemetryService** — `+/telemetry` subscriber, veri kaydetme
5. **AgentLocationService** — `+/location` subscriber, konum kaydetme + geofence evaluation
6. **AgentEventService** — `+/events` subscriber, event isleme
7. **AgentCommandService** — Komut olusturma + MQTT publish
8. **AgentCommandResponseHandler** — `+/responses` subscriber, komut durumu guncelleme
9. **DB Entities** — `AgentTelemetry`, `DeviceLocation`, `DevicePresenceLog`, `AgentCommand`, `Geofence`
10. **REST API** — Frontend icin konum gecmisi, telemetri, presence durumu, komut gonderme endpoint'leri
11. **ApplePolicyApplicationServiceImpl TODO'yu doldur** — Agent policy publish (`update_app_policy` komutu)

### 7.2 iOS Agent

1. MQTT client (CocoaMQTT)
2. Background location mode + permission handling
3. Heartbeat (MQTT connect + LWT)
4. Konum publisher (periyodik + on-demand)
5. Telemetri collector (battery, storage, network)
6. Command subscriber + handler
7. APNS entegrasyonu (MQTT baglantisi koptugunda wake-up)
8. Geofence monitoring (CLCircularRegion)
9. Compliance check (jailbreak detection)

### 7.3 macOS Agent

1. LaunchDaemon / LaunchAgent setup
2. MQTT client (persistent connection)
3. Heartbeat + telemetri + konum (iOS ile ortak protokol)
4. App blocking enforcement (running process monitoring)
5. Script execution handler
6. Command subscriber + handler
7. Ekran paylasimi (Faz 2)

---

## 8. Mimari Diagram

```
Client Side (ayri):            Server Side (tek):

+-----------------+             +----------------------------+
|  macOS Agent    |---MQTT----->|                            |
|  (LaunchDaemon) |<------------|   apple_mdm module         |
+-----------------+             |   +- AgentMqttConfig       |
                                |   +- AgentPresenceService  |
+-----------------+             |   +- AgentTelemetryService |
|  iOS Agent      |---MQTT----->|   +- AgentLocationService  |
|  (iOS App)      |<------------|   +- AgentEventService     |
+-----------------+             |   +- AgentCommandService   |
                                |   +- AgentCommandResponse  |
                                |      Handler               |
                                +----------------------------+
                                             |
                                    +--------+--------+
                                    |   PostgreSQL    |
                                    |   Redis         |
                                    +-----------------+
```

---

## 9. MQTT Broker Gereksinimleri

- **Broker**: EMQX veya HiveMQ (production), Mosquitto (development)
- **Authentication**: Username/password veya client certificate (device UDID bazli)
- **ACL**: Her cihaz sadece kendi topic'lerine publish/subscribe yapabilmeli
- **LWT Destegi**: Zorunlu (heartbeat icin)
- **Retained Messages**: Zorunlu (status icin)
- **QoS 2 Destegi**: Zorunlu (komutlar icin)
