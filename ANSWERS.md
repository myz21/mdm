# Apple MDM Komut Sistemi - Detaylı Çözüm Analizi

Sorular aşağıdadır ama öncelikle mevcut adımların kodu yapısını inceledim ve yorumum aşağıdadır.

```
İlk önce 1. ve 2. adımı okuduktan sonra burada bir API isteği olduğunu fark ettim. Sonra CONTEXT.md'de, cihazı kilitle yazıdınız için "lock" keyword'ünü arattım. Ve aşağıdaki endpoint'i buldum:

```
/devices/{udid}/commands/lock	
```

Daha sonra `commands/lock`'u codebase üzerinde aradım. Aşağıdaki koda ulaştım:

```java
    @Operation(summary = "Send DeviceLock Command")
    @PostMapping("/{udid}/commands/lock")
    public ResponseEntity<Map<String, Object>> lockDevice(@PathVariable String udid,
                             @RequestParam(required = false) String message,
                             @RequestParam(required = false) String phoneNumber) throws Exception {
        appleCommandSenderService.lockDevice(udid, message, phoneNumber);
        return ResponseEntity.ok(commandResponse("DeviceLock", udid));
    }
```

Bu methoddan ilk anladığım, bunun mesaj ve telefon numarası alabileceği idi. Cihazı kiltleyen iç method yani lockDevice id, message, phoneNumber parametrelerini almakta. Bunlar da id request header üzerinden, telefon numarası ve mesaj da request body üzerinden gelmektedir. Dönüş olarak DeviceLock mesajı ile birlikte udid dönmektedir.

AppleCommandService'in altındaki LockDevice'a gittim. Sonra appleDeviceRepository'deki findByUdid methodu cihazın id'sini alarak veri tabanına sorgu atmaktadır. Eğer ki sorgu sonucu boş ise böyle bir id sistemimizde kayıtlı değil demektir. Ve kilitleme işleminin iptal olduğuna dair log basılır. 

Daha sonra cihazın id'si alınarak cihazı kilitlemeyi temsil eden random bir id createCommandUUID ile üretilir.

Daha sonra bu komutun id'si, cihazın id'si, komutun template'i ve kilitleme komutu appleCommandQueueService'e (kuyruk) push edilir.

Bu push'un bir interface'e bağlı olduğunu gördüm. Daha sonra pushCommand'ı codebase içerisinde incelediğimde RedisAppleCommandQueueServiceImpl'ya gittim. Daha sonra bunun üzerinde bir yorum satırı fark ettim.

// NOTE: pushCommand must NOT be @Async — callers push multiple commands sequentially
    // and rely on FIFO ordering. Making this async breaks deterministic queue order.

Burada bu methodun asenkron olmaması gerektiğini çünkü asenkron olduğunda FIFO mantığının bozulabileceğinden dolayı böyle bir yorum satırı bırakıldığını anladım. 

Bu methodda öncelikle komut depolama için bir XML'e dönüştürülüyor. Sonra komut database'e yazılıyor. Sonrasında redis kuyruğuna ekleniyor ama önce kuyruk boş mu diye bir kontrol ediliyor. Sonra eğer mevcutta herhangi bir komut kuyruk üzerinde koşmuyorsa ve kuyruğa ilk defa komut giriyorsa bu durumda device weak ediliyor. Eğer sendWakeUp FAIL olursa hata mesajı ID ile birlikte loglanıyor. Eğer ki başarılı şekilde pushlanıdysa ID ve komut IDsi loglanıyor. İlk üç adımı tamalamış oluyoruz.

Sonrasında cihaz WAKE komutunu aldı ve artık 4. adımdayız. Burada cihaz /mdm/connect'e bağlanır. Bununla alakalı dosya `/home/neo/Desktop/GITHUB MYZ21/apple_mdm/src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/AppleCommandSenderServiceImpl.java`'te görülebilir. 

Gerekli validasyonlardan sonra popCommand ile sonraki komut pop edilir.

Daha sonra toXMLPropertyList methodu ile XML'e çevirme işlemi gerçekleştirilerek cihazın anlayacağı formata çevrilir. Buradaki pop command EXECUTING için incelendiğinde öncelikle cihazın başka bir instance tarafından o an herhangi bir komutu işleyip işlemediği kontrol edilir. Eğer işleniyorsa işlem pas geçilir. inFlight durumu için ayrıca bir yorum satırı bırakıldığını da gördüm: `// If this device already has an in-flight command, do NOT issue another one`.

Kuyruktan alınan komut `executeCommandAsync` ile çalıştırılır. Ayrıca bu işlemin gerçekleştirilmesi bir zaman harcayacağından dolayı bir TTL cache kullanılmıştır. Cihaz komutu çalıştırıldıkta sonra sonucu döner.

Burada Acknowledged, Error veya NotNow şeklinde cevabı döndüğünü görüyorum.

Daha sonra kuyruk ve inflight temizliği, sonraki komuta geçme, db durumu güncelleme gibi şeyler yapılır. 
```


## a) Veri Yapısı Seçimi

---

### a1) Komutların sırasını korumak için hangi veri yapısı?

#### Detaylı Cevap:
Projede **QUEUE (İlk Gelen İlk Çıkan - FIFO)** veri yapısı tercih edilmiştir. 

**Sebebi:**
- Komutlar sırasıyla gelmesi gerekir (WiFi profili → uygulama yükleme → cihaz kilitleme)
- Her komut önceki komutun tamamlanmasını beklemesi gerekir
- Redis **List** veri yapısı LPOP (soldan çıkar) ve RPUSH (sağdan ekle) ile FIFO sağlar
- Komutlar tek seferde cihaza verilmez, stale state'in telafi edilebilmesi için komut sırasının korunması zorunludur

#### Kod Konumu:
[src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L56-L57)

```java
private static final String QUEUE_KEY_PREFIX = "apple:command:queue:";

// Redis List yapısı — her cihaz için ayrı FIFO queue
String queueKey = QUEUE_KEY_PREFIX + udid;
redisTemplate.opsForList().rightPush(queueKey, item);  // Sağdan ekle (RPUSH)
Object head = redisTemplate.opsForList().index(queueKey, 0);  // İlk elemanı oku
```

#### Alternatif ve Değişiklik:

**1) Stack (LIFO) - Önerilmez:**
- En son komut önce çalışır → Sıra bozulur
- Politika uygulanması hatalı sonuç verir

**2) Priority Queue:**
- Komutlara öncelik seviyeleri eklenebilir
- Değişiklik: RedisTemplate'de `opsForZSet()` kullanılır
- Dosya: [RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L350-L360)

```java
// Mevcut (List - FIFO):
Object queueItem = redisTemplate.opsForList().index(queueKey, 0);

// Alternatif (Sorted Set - Priority):
Set<TypedTuple<Object>> items = redisTemplate.opsForZSet()
    .rangeWithScores(queueKey, 0, 0);  // score'u en düşük olanı al (yüksek öncelik)
```

---

### a2) Belleğe mi yoksa harici yerde mi tutulmalı? Sunucu restart durumu?

#### Detaylı Cevap:
Projede **hem Redis'e hem de veritabanına** yazılır (**Dual-Write Pattern**):

| Hedef | Sebep | Faydası |
|-------|-------|---------|
| **PostgreSQL (DB)** | Persistence/Durability | Sunucu çökse bile veriler disk'te kalır |
| **Redis (Cache)** | Performance | Hızlı okuma; veritabanını overload etmez |

**Sunucu yeniden başlatılsa bile komut kaybolmaz:**
1. `loadPendingCommandsFromDatabase()` metodu otomatik çalıştırılır (@PostConstruct)
2. Veritabanından PENDING ve EXECUTING komutlar yüklenir
3. EXECUTING durumundaki komutlar PENDING'e geri alınır (stale commands temizlenir)
4. Redis'e yeniden yüklenir ve cihazlara wake-up push gönderilir

#### Kod Konumu:
[src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L119-L195)

```java
@PostConstruct
public void loadPendingCommandsFromDatabase() {
    // Multi-instance coordination: başlangıç lock
    Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(STARTUP_LOAD_LOCK, "loading", Duration.ofMinutes(2));
    if (!Boolean.TRUE.equals(acquired)) {
        logger.info("Another instance is loading pending commands, skipping");
        return;  // Başka instance zaten yüklüyor
    }

    logger.info("Loading pending and executing commands from database into Redis on startup...");
    try {
        // DB'den PENDING ve EXECUTING komutları yükle
        List<AppleCommand> pendingCommands = appleCommandRepository.findPendingAndExecutingCommands();
        
        for (AppleCommand command : pendingCommands) {
            String udid = command.getAppleDeviceUdid();
            
            // Stale EXECUTING komutlarını PENDING'e al
            if (CommandStatus.EXECUTING.name().equals(command.getStatus())) {
                logger.info("Resetting stale EXECUTING command {} back to PENDING", 
                    command.getCommandUUID());
                String resetQuery = "UPDATE apple_command SET status = ?, execution_time = NULL " +
                        "WHERE command_uuid = ?";
                jdbcTemplate.update(resetQuery, CommandStatus.PENDING.name(), 
                    command.getCommandUUID());
            }

            // Stale inflight key'i temizle
            redisTemplate.delete(getInflightKey(udid));

            // Redis queue'ye ekle
            String queueKey = getQueueKey(udid);
            RedisQueueItem item = new RedisQueueItem(command.getCommandUUID(), 
                command.getTemplate());
            redisTemplate.opsForList().rightPush(queueKey, serializeQueueItem(item));
        }

        logger.info("Loaded {} pending/executing commands from database into Redis", 
            pendingCommands.size());

        // Bekleyen komutları olan cihazlara wake-up push gönder
        Set<String> udidsWithPending = new HashSet<>();
        for (AppleCommand command : pendingCommands) {
            if (command.getAppleDeviceUdid() != null) {
                udidsWithPending.add(command.getAppleDeviceUdid());
            }
        }
        
        for (String udid : udidsWithPending) {
            sendWakeUp(udid);
        }
    } finally {
        redisTemplate.delete(STARTUP_LOAD_LOCK);
    }

    // 30 saniye periyotla stale komutları temizleme reaper'ını başlat
    scheduler.scheduleAtFixedRate(this::expireStaleInFlightCommands, 30, 30, TimeUnit.SECONDS);
}
```

#### Alternatif ve Değişiklik:

**Event Sourcing (Kafka/RabbitMQ):**
- Daha modern ve asenkron yaklaşım
- Dosya: [RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L280-L310)

```java
// Mevcut (Dual-write: DB + Redis):
appleCommandRepository.save(appleCommand);  // DB'ye yaz
String queueKey = QUEUE_KEY_PREFIX + udid;
redisTemplate.opsForList().rightPush(queueKey, item);  // Redis'e yaz

// Alternatif (Event-driven):
// 1. appleCommandRepository.save(appleCommand);
// 2. kafkaTemplate.send("apple-commands", appleCommand);
// 3. KafkaListener, DB'den oku ve Redis'e yükle (asyen, daha güvenli)
```

---

### a3) Projede kullanılan yapı: RedisAppleCommandQueueServiceImpl

#### Detaylı Cevap:
**Redis List** kullanılmıştır ve yönetim şu şekildedir:

1. **Queue**: Her cihaz için ayrı key (`apple:command:queue:{udid}`)
2. **FIFO Operasyonu**: 
   - `RPUSH`: Sağdan ekle (yeni komut)
   - `LPOP`: Soldan çıkar (komut tamamlandı)
   - `LINDEX 0`: İlk elemanı oku (komut ver)
3. **Atomic Operations**: Lua Scripts ile race condition'ı önler

#### Kod Konumu:
[src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L1-L100)

```java
public class RedisAppleCommandQueueServiceImpl implements AppleCommandQueueService {

    private static final String QUEUE_KEY_PREFIX = "apple:command:queue:";
    private static final String INFLIGHT_KEY_PREFIX = "apple:command:inflight:";
    private static final String LOCK_KEY_PREFIX = "apple:command:lock:";

    // Redis List yapısı — FIFO pattern
    private String getQueueKey(String udid) {
        return QUEUE_KEY_PREFIX + udid;
    }

    // Komut kuyruga ekleme (sağdan)
    public void pushCommand(String udid, String commandUUID, NSDictionary command, ...) {
        String queueKey = getQueueKey(udid);
        RedisQueueItem item = new RedisQueueItem(commandUUID, commandXml);
        redisTemplate.opsForList().rightPush(queueKey, serializeQueueItem(item));  // RPUSH
    }

    // Komut kuyruktan çıkarma (soldan)
    private long removeCommandFromQueue(String queueKey, String commandUUID) {
        Long result = redisTemplate.execute(
                REMOVE_QUEUE_HEAD_SCRIPT,  // Lua script: atomik LINDEX + LPOP
                List.of(queueKey),
                commandUUID
        );
        return result != null ? result : -1;
    }
}
```

#### Alternatif ve Değişiklik:

**Redis Streams (Daha Modern):**
- Consumer groups ile otomatik fairness
- Dosya: [RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L56-L57)

```java
// Mevcut (List + Lock):
private static final String QUEUE_KEY_PREFIX = "apple:command:queue:";
private static final String LOCK_KEY_PREFIX = "apple:command:lock:";

// Alternatif (Streams):
// XREAD GROUP mygroup myconsumer COUNT 1 STREAMS apple:commands:udid $
// Redis otomatik olarak:
// - Surum kilit yönetir
// - Acknowledgment (XACK) ile idempotency
// - Pending list ile dead-letter handling
```

---

## b) Komut Durumları

---

### b1) Komut durumları ne anlama gelir?

#### Detaylı Cevap:

| Durum | Anlamı | Teknik Açıklama | Yaşam Süresi |
|-------|--------|-----------------|-------------|
| **PENDING** | Komut hazırlanmış, söylenmeyi bekliyor | Veritabanında tutulur, Redis queue'ye konur | İlk oluşturulmadan komut verilişine kadar |
| **EXECUTING** | Cihaza iletildi, cihaz çalıştırıyor | In-flight set'ine konur, timeout başlar (5 dk) | Komut cihaza gönderildikten cihazdan yanıt alınana kadar |
| **COMPLETED** | Cihaz komut sonucu geri gönderdi | completionTime kaydedilir, başarılı işlem | Cihazdan başarılı yanıt alındığında |
| **FAILED** | Cihaz hata rapor etti veya timeout oldu | failureReason kaydedilir, queue'den çıkar | Cihazdan hata yanıtı alındığında veya 5 dakika bittikten sonra |
| **CANCELED** | Admin tarafından iptal edildi | Komut çalıştırılmaz, queue'den silinir | Admin iptal ettiğinde |

#### Kod Konumu:
[src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L974-L980)

```java
public enum CommandStatus {
    PENDING,      // Komut hazırlanmış, bekliyor
    EXECUTING,    // Cihaz işleniyor, timeout başladı
    COMPLETED,    // Başarıyla tamamlandı
    FAILED,       // Hata veya timeout
    CANCELED      // Admin tarafından iptal
}
```

#### Alternatif ve Değişiklik:

**Extended Status System:**
- QUEUED, ACKNOWLEDGED, IN_PROGRESS, RETRYING, PARTIALLY_FAILED, SKIPPED durumları eklenebilir
- Dosya: [RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L974-L985)

```java
// Mevcut (5 status):
public enum CommandStatus {
    PENDING, EXECUTING, COMPLETED, FAILED, CANCELED
}

// Alternatif (9 status - retry mekanizması):
public enum CommandStatus {
    PENDING,              // Yeni komut
    QUEUED,               // Cihazda queue'de
    ACKNOWLEDGED,         // Cihaz çalışacağını bildirdi
    EXECUTING,            // Cihaz çalışıyor
    RETRYING,             // Başarısız, retry bekliyor (1. retry)
    PARTIALLY_FAILED,     // Kısmi başarı (multi-step komut)
    COMPLETED,            // Başarıyla tamamlandı
    FAILED,               // Kalıcı hata
    CANCELED              // Admin tarafından iptal
}
```

---

### b2) Neden komutlar tek tek sırayla veriliyor, hep beraber değil?

#### Detaylı Cevap:

**Bu tasarım kararının sebepleri:**

1. **Cihaz Kapasitesi**: Bir cihaz aynı anda bir komutu işleyebilir
   - Birden fazlasını göndermek cihazı overload edebilir
   - MDM protokolü kısıtlı bant genişliğinde tasarlandı

2. **Sıra Bağımlılığı**: 
   - WiFi profili kurulması → Internet bağlantısı → App kurulması
   - Profil olmadan app kurulamazsa işe yaramaz
   - Her komutun başarısı sonraki komutun çalışmasını etkiler

3. **State Tracking**: 
   - Sistemin her komutun sonucunu bilemesi gerekir
   - `execution_time`, `completion_time`, `failureReason` takip edilir
   - Concurrent komutlarda state kaybı riski

4. **Queue Pattern Implementasyon**: 
   - In-flight mekanizması ile aynı anda sadece 1 komut tutulur
   - `apple:command:inflight:{udid}` sadece bir komut tutar

#### Kod Konumu:
[src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L310-L350)

```java
@Override
public Map.Entry<String, NSDictionary> popCommand(String udid) throws Exception {
    logger.info("Attempting to pop the next command for device with UDID: {}", udid);

    // Acquire per-device distributed lock
    String lockKey = LOCK_KEY_PREFIX + udid;
    String lockValue = UUID.randomUUID().toString();
    Boolean acquired = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, lockValue, LOCK_TTL);
    if (!Boolean.TRUE.equals(acquired)) {
        logger.info("Device {} is being processed by another instance. Skipping.", udid);
        return null;  // Başka instance işliyor
    }

    try {
        String inflightKey = getInflightKey(udid);
        String queueKey = getQueueKey(udid);

        // *** ÖNEMLİ: Cihazda zaten bir komut işleniyor mu? ***
        Object inflightCommand = redisTemplate.opsForValue().get(inflightKey);
        if (inflightCommand != null) {
            logger.info("Device {} already has an in-flight command. Returning no command.", udid);
            return null;  // Yeni komut VERMEZ! Beklemeye devam et
        }

        // Sadece bir komut verilir
        Object queueItem = redisTemplate.opsForList().index(queueKey, 0);
        if (queueItem == null) {
            return null;  // Queue boş
        }

        RedisQueueItem item = deserializeQueueItem(queueItem);
        if (item == null) return null;

        logger.info("Issuing command with UUID: {} for device with UDID: {}", 
            item.uuid(), udid);

        // Komut EXECUTING olarak işaretlen ve in-flight'a set et
        this.executeCommandAsync(item.uuid());
        redisTemplate.opsForValue().set(inflightKey, item.uuid(), IN_FLIGHT_TTL);

        // Komut cihaza gönder
        NSDictionary commandDict = (NSDictionary) PropertyListParser.parse(
                item.commandXml().getBytes(StandardCharsets.UTF_8)
        );

        return new AbstractMap.SimpleEntry<>(item.uuid(), commandDict);
    } finally {
        // Kilit serbest bırak
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), lockValue);
    }
}
```

#### Alternatif ve Değişiklik:

**Concurrent Command Processing (Kontrollü Parallelism):**
- `inFlightLimit` parametresi ile 1 yerine 2-3 komut paralel çalışabilir
- Dosya: [RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L318-L330)

```java
// Mevcut (1 komut):
private static final int MAX_INFLIGHT_COMMANDS = 1;

// Alternatif (3 komut paralel - riskli):
private static final int MAX_INFLIGHT_COMMANDS = 3;

// Değişiklik: Array/List kullan
private Map<String, List<String>> inflightCommands = new ConcurrentHashMap<>();

// popCommand'da kontrol et:
List<String> inFlight = inflightCommands.getOrDefault(inflightKey, new ArrayList<>());
if (inFlight.size() >= MAX_INFLIGHT_COMMANDS) {
    return null;  // Limit aşıldı
}
```

---

### b3) Cihaz uzun süre cevap vermezse sistem ne yapmalı?

#### Detaylı Cevap:

Projede **timeout ve reaper mekanizması** uygulanmıştır:

**Timeout Değeri**: 5 dakika (`IN_FLIGHT_TTL`)

**Mekanizma:**
1. Her 30 saniyede bir `expireStaleInFlightCommands()` metodu çalışır
2. EXECUTING durumundaki komutları kontrol eder
3. 5 dakikayı geçen komutlar **FAILED** olarak işaretlenir
4. failureReason: *"Command timed out after 5 minutes (no device response)"*
5. Redis'ten temizlenir ve bir sonraki komut verilir
6. Etkilenen cihaza APNs wake-up gönderilir

#### Kod Konumu:
[src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L59-L60) ve [satır 189-225]

```java
// Timeout konfigürasyonu
private static final Duration IN_FLIGHT_TTL = Duration.ofMinutes(5);
private static final Duration LOCK_TTL = Duration.ofSeconds(10);

// Reaper mekanizması
private void expireStaleInFlightCommands() {
    try {
        // Distributed lock: sadece bir instance çalışır
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(REAPER_LOCK_KEY, "1", Duration.ofSeconds(25));
        if (!Boolean.TRUE.equals(acquired)) {
            return;  // Başka instance zaten çalışıyor
        }

        try {
            // 5 dakikadan eski EXECUTING komutlarını bul
            Instant cutoff = Instant.now().minus(IN_FLIGHT_TTL);
            String query = "SELECT command_uuid, apple_device_udid FROM apple_command " +
                    "WHERE status = 'EXECUTING' AND execution_time < ?";
            List<Map<String, Object>> staleCommands = jdbcTemplate
                .queryForList(query, Timestamp.from(cutoff));

            for (Map<String, Object> row : staleCommands) {
                String commandUuid = (String) row.get("command_uuid");
                String udid = (String) row.get("apple_device_udid");

                logger.warn("Reaper: expiring stale command {} for device {} " +
                    "(exceeded 5 min TTL)", commandUuid, udid);

                // DB'de FAILED olarak işaretle
                failCommand(commandUuid,
                        "Command timed out after 5 minutes (no device response)");

                // Redis temizle
                if (udid != null) {
                    String queueKey = getQueueKey(udid);
                    String inflightKey = getInflightKey(udid);

                    removeCommandFromQueue(queueKey, commandUuid);
                    redisTemplate.delete(inflightKey);

                    // Sonraki komut için cihaza uyar
                    try {
                        sendWakeUp(udid);
                    } catch (Exception ignored) {
                    }
                }
            }

            if (!staleCommands.isEmpty()) {
                logger.info("Reaper: expired {} stale commands", staleCommands.size());
            }

            // Orphaned in-flight entries'i da temizle
            String orphanQuery = "SELECT DISTINCT apple_device_udid FROM apple_command " +
                    "WHERE status IN ('COMPLETED', 'FAILED', 'CANCELED') " +
                    "AND completion_time < NOW() - INTERVAL '1 minute'";
            List<String> deviceUdids = jdbcTemplate.queryForList(orphanQuery, String.class);

            int orphansCleaned = 0;
            for (String udid : deviceUdids) {
                String inflightKey = getInflightKey(udid);
                Object inflightVal = redisTemplate.opsForValue().get(inflightKey);
                if (inflightVal != null) {
                    String inflightUuid = inflightVal.toString();
                    String statusCheck = "SELECT status FROM apple_command WHERE command_uuid = ?";
                    List<String> statuses = jdbcTemplate
                        .queryForList(statusCheck, String.class, inflightUuid);
                    if (!statuses.isEmpty()) {
                        String cmdStatus = statuses.get(0);
                        if ("COMPLETED".equals(cmdStatus) || "FAILED".equals(cmdStatus) 
                            || "CANCELED".equals(cmdStatus)) {
                            redisTemplate.delete(inflightKey);
                            orphansCleaned++;
                            logger.warn("Reaper: cleared orphaned in-flight {} for device {} " +
                                "(status={})", inflightUuid, udid, cmdStatus);
                            try { sendWakeUp(udid); } catch (Exception ignored) {}
                        }
                    }
                }
            }
            if (orphansCleaned > 0) {
                logger.info("Reaper: cleared {} orphaned in-flight slots", orphansCleaned);
            }
        } finally {
            redisTemplate.delete(REAPER_LOCK_KEY);
        }
    } catch (Exception e) {
        logger.error("Error in Redis in-flight command reaper: {}", e.getMessage(), e);
    }
}

// Startup'ta reaper'ı başlat (30 saniye periyot)
scheduler.scheduleAtFixedRate(this::expireStaleInFlightCommands, 30, 30, TimeUnit.SECONDS);
```

#### Alternatif ve Değişiklik:

**Adaptive Timeout (Cihaz Türüne Göre Değişen):**
- iOS: 5 dk, macOS: 10 dk, Apple Watch: 3 dk
- Dosya: [RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L59-L60)

```java
// Mevcut (Sabit 5 dakika):
private static final Duration IN_FLIGHT_TTL = Duration.ofMinutes(5);

// Alternatif (Platform dönemine göre):
private Duration getTimeoutForDevice(String productName) {
    if (productName != null) {
        if (productName.toLowerCase().contains("iphone") || 
            productName.toLowerCase().contains("ipad")) {
            return Duration.ofMinutes(5);  // iOS
        } else if (productName.toLowerCase().contains("mac")) {
            return Duration.ofMinutes(10);  // macOS
        } else if (productName.toLowerCase().contains("watch")) {
            return Duration.ofMinutes(3);  // watchOS
        }
    }
    return Duration.ofMinutes(5);  // Default
}

// expireStaleInFlightCommands'i değiştir:
for (Map<String, Object> row : staleCommands) {
    String productName = (String) row.get("product_name");
    Duration timeout = getTimeoutForDevice(productName);
    Instant cutoff = Instant.now().minus(timeout);
    // ... rest of logic
}
```

---

## c) Birden Fazla Sunucu (Multi-Instance)

---

### c1) Aynı komut iki instance tarafından okunursa ne olur?

#### Detaylı Cevap:

**Race Condition** oluşur:
- Instance A komutu okur, durunu EXECUTING'e alır
- Instance B aynı komutu tekrar okur, ikinci kez iletir
- Cihaz aynı komutu iki kez çalıştırır → **Sistem tutarsızlığı**

**Senaryo Örneği:**
```
Instance A: "iPhone'a iOS Update başlat" → EXECUTING (DB'ye yazıldı)
Instance B: Redis'ten aynı komutu okudu (DB güncellemesi henüz propagate olmadı)
Instance B: "iPhone'a iOS Update başlat" → Tekrar EXECUTING
Cihaz: İki kez update başlarsa sistem bozulabilir → Çakışma, korrupsiyon
```

**Sonuç:**
- Komut iki kez uygulanması
- Politika iki kez kurulması
- Cihaz kilidi iki kez uygulanması (sistem çökmesi)

#### Kod Konumu:
[src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L310-L330)

```java
// PROBLEM: Hiçbir lock olmadan race condition oluşur
Object queueItem = redisTemplate.opsForList().index(queueKey, 0);
// Instance A ve B aynı anda şu satırı okuyabilir → İKİ KERE BİLDİRİM

this.executeCommandAsync(item.uuid());  // CMD 1
// ... Instance B aynı komutla buraya gelir ...
this.executeCommandAsync(item.uuid());  // CMD 2 (ÇAKIŞMA!)
```

---

### c2) Race condition'ı önlemek için hangi mekanizma?

#### Detaylı Cevap:

Projede **Distributed Lock (Dağıtık Kilit)** kullanılır:

**Mekanizma:**
1. **Lock Acquisition**: Instance, komut almadan önce Redis'te lock yaratmaya çalışır
2. **SET IF ABSENT**: Eğer lock zaten varsa, o instance komutu almaz
3. **TTL**: Lock otomatik olarak 10 saniyede silinir (deadlock'ı önler)
4. **Lua Scripts**: Kilidi salırırken başka instance tarafından alınmaz (atomic)

**Şema:**
```
Instance A: SET "apple:command:lock:UDID" "UUID-A" NX EX 10 → SUCCESS
Instance B: SET "apple:command:lock:UDID" "UUID-B" NX EX 10 → FAILED (lock var)
Instance B: Return null (komut alma)

Instance A: Komut işle
Instance A: Lua Script ile kilidi serbest bırak (sadece sahibi açabilir)
Instance B: SET "apple:command:lock:UDID" "UUID-B" NX EX 10 → SUCCESS (kilit açıldı)
```

#### Kod Konumu:
[src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L310-L350)

```java
@Override
public Map.Entry<String, NSDictionary> popCommand(String udid) throws Exception {
    logger.info("Attempting to pop the next command for device with UDID: {}", udid);

    // *** PER-DEVICE DISTRIBUTED LOCK ***
    String lockKey = LOCK_KEY_PREFIX + udid;
    String lockValue = UUID.randomUUID().toString();  // Unique token
    
    Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, lockValue, LOCK_TTL);  // 10 saniye
    
    if (!Boolean.TRUE.equals(acquired)) {
        logger.info("Device {} is being processed by another instance. Skipping.", udid);
        return null;  // Lock alınamadı, çık
    }

    try {
        // Kilit altında komut işlemleri...
        
        String inflightKey = getInflightKey(udid);
        String queueKey = getQueueKey(udid);

        // In-flight komut var mı?
        Object inflightCommand = redisTemplate.opsForValue().get(inflightKey);
        if (inflightCommand != null) {
            logger.info("Device {} already has an in-flight command. No command.", udid);
            return null;
        }

        // Kuyruktan ilk komutu al
        Object queueItem = redisTemplate.opsForList().index(queueKey, 0);
        // ... komut işleme ...

        return new AbstractMap.SimpleEntry<>(item.uuid(), commandDict);
    } finally {
        // *** LUA SCRIPT İLE ATOMIC LOCK RELEASE ***
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), lockValue);
    }
}
```

---

### c3) Projede LOCK_KEY_PREFIX nasıl uygulanmış?

#### Detaylı Cevap:

Redis'te her cihaz için ayrı bir lock key oluşturulur:

```
LOCK_KEY_PREFIX = "apple:command:lock:"
Full Key = "apple:command:lock:{udid}"

Örnek: "apple:command:lock:A1B2C3D4-E5F6-7890-ABCD-EF1234567890"
```

**Lua Script ile Atomic Release** (çok önemli):

#### Kod Konumu:
[src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L63-L66) ve [satır 56]

```java
private static final String LOCK_KEY_PREFIX = "apple:command:lock:";

// *** LUA SCRIPT: LOCK RELEASE ONLY IF WE STILL HOLD IT ***
private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "    return redis.call('del', KEYS[1]) " +
        "else " +
        "    return 0 " +
        "end",
        Long.class  // 1 (başarı) veya 0 (başarısız: kilit sahibi değil)
);

// Kullanım:
redisTemplate.execute(RELEASE_LOCK_SCRIPT, 
    Collections.singletonList(lockKey),  // KEYS[1]
    lockValue);                           // ARGV[1]
```

**Script Açıklaması:**
- `KEYS[1]` = Lock key (örn: "apple:command:lock:UDID")
- `ARGV[1]` = Lock value (Instance'ın UUID'si)
- `if redis.call('get', KEYS[1]) == ARGV[1]` = Yakında kilide sahibi ben miyim?
- `redis.call('del', KEYS[1])` = Kilidi sil (sadece sahibi silebilir)
- Sonuç: 1 (silindi) veya 0 (sahibi değilim)

**Neden Lua Script gerekli?**
- Redis'te GET + DEL iki işlem olmasaydı:
  - Instance A: GET → "UUID-A" (benim kilidi)
  - Instance B: SET → Override! (lock çalındı)
  - Instance A: DEL (çalınan kilidi sildi)
  - **ATOMICITY BOZULDU**

#### Alternatif ve Değişiklik:

**Redis Streams Consumer Groups:**
- Otomatik kilitleme ve fairness
- Dosya: [RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L56-L57)

```java
// Mevcut (List + Explicit Lock):
private static final String QUEUE_KEY_PREFIX = "apple:command:queue:";
private static final String LOCK_KEY_PREFIX = "apple:command:lock:";

// Alternatif (Streams + Consumer Groups):
// XREAD GROUP mygroup myconsumer COUNT 1 STREAMS apple:commands:udid $
// - Redis otomatik olarak single consumer garantisi sağlar
// - XACK ile idempotency
// - Explicit lock yönetimi gerekmez
```

---

## d) Sunucu Çökerse Ne Olur?

---

### d1) Komut kaydedildikten sonra sunucu çökerse, komut kaybolur mu?

#### Detaylı Cevap:

**HAYIR, komut kaybolmaz çünkü:**

1. **Database Persistence**: Komut PostgreSQL veritabanına yazılır (**disk'te permanent**)
2. **Redis Caching**: Aynı zamanda Redis'e yazılır (hızlı erişim için)
3. **Dual-Write Pattern**: İki yerden biri çökmüş olsa bile diğeri veri tutar

**Senaryo:**
```
1. Admin: "Cihazı kilitle" (komut gönder)
2. Sistem: appleCommandRepository.save() → Database WRITE (başarı)
3. Sistem: redisTemplate.opsForList().rightPush() → Redis WRITE
4. Sistem: applePushService.sendMdmWakeUp() → APNs push gönder
5. ⚠️ [Sunucu burada ÇÖKSE bile]
5. Yeni sunucu başladığında:
   - Veritabanından PENDING komutu yükler
   - Redis'e yeniden ekler
   - Cihaza uyar
   - Komut normal şekilde devam eder
```

**Sonuç:** Komut HIÇBIR ZAMıN kaybolmaz.

#### Kod Konumu:
[src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L435-L470) (pushCommand metodu)

```java
@Override
public void pushCommand(String udid, String commandUUID, NSDictionary command, 
        String commandType, boolean isSystem, boolean fromPolicy, UUID policyId) 
        throws Exception {

    logger.info("Pushing command to the queue. UDID: {}, Command UUID: {}, " +
        "Command Type: {}", udid, commandUUID, commandType);

    // 1. Komut XML'e dönüştür
    String commandXml = ddPlistToXml(command);

    // 2. *** VERITABANINA KAYDET (PERSISTENT) ***
    logger.debug("Saving AppleCommand to the database...");
    this.saveAppleCommandAsync(udid, commandUUID, command, commandType, 
        fromPolicy, policyId, isSystem);

    // 3. *** REDIS'E EKLE (CACHE) ***
    logger.debug("Adding command to the Redis queue. UDID: {}, Command UUID: {}", 
        udid, commandUUID);
    String queueKey = getQueueKey(udid);
    String inflightKey = getInflightKey(udid);

    Long queueSize = redisTemplate.opsForList().size(queueKey);
    boolean wasEmpty = queueSize == null || queueSize == 0;

    RedisQueueItem item = new RedisQueueItem(commandUUID, commandXml);
    redisTemplate.opsForList().rightPush(queueKey, serializeQueueItem(item));

    // 4. Kuyruk boş ve in-flight yoksa APNs wake-up gönder
    Object inflightCommand = redisTemplate.opsForValue().get(inflightKey);
    if (wasEmpty && inflightCommand == null) {
        try {
            sendWakeUp(udid);
        } catch (Exception e) {
            logger.warn("Failed to send wake-up push for UDID {}: {}", udid, 
                e.getMessage());
        }
    }

    logger.info("Command successfully pushed to the queue. UDID: {}, " +
        "Command UUID: {}", udid, commandUUID);
}

// Asenkron DB kayıt
@Async
public void saveAppleCommandAsync(String udid, String commandUUID, 
        NSDictionary commandTemplate, String commandType, boolean fromPolicy, 
        UUID policyId, boolean isSystem) throws Exception {

    logger.info("Starting to save AppleCommand asynchronously...");

    var deviceOpt = appleDeviceRepository.findByUdid(udid);
    if (deviceOpt.isEmpty()) {
        deviceOpt = appleDeviceRepository.findByEnrollmentId(udid);
    }

    if (deviceOpt.isEmpty()) {
        logger.warn("Device with identifier '{}' not found", udid);
        return;
    }

    AppleCommand command = AppleCommand.builder()
            .appleDevice(deviceOpt.get())
            .commandUUID(commandUUID)
            .commandType(commandType)
            .template(ddPlistToXml(commandTemplate))
            .status(CommandStatus.PENDING.name())
            .requestTime(Instant.now())
            .build();

    if (fromPolicy) {
        Optional<Policy> policyOpt = policyRepository.findById(policyId);
        if (policyOpt.isPresent()) {
            command.setPolicy(policyOpt.get());
        }
    }

    logger.debug("Saving AppleCommand to the database...");
    appleCommandRepository.save(command);  // *** DB'ye yazıldı ***
    logger.info("AppleCommand successfully saved with UUID: {}", commandUUID);
}
```

---

### d2) Sunucu başlangıcında "kurtarma" mekanizması var mı?

#### Detaylı Cevap:

**EVET**, `loadPendingCommandsFromDatabase()` metodu (@PostConstruct) kurtarma mekanizmasıdır:

#### Kod Konumu:
[src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L119-L195)

```java
@PostConstruct
public void loadPendingCommandsFromDatabase() {
    // 1. Startup lock (multi-instance coordination)
    Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(STARTUP_LOAD_LOCK, "loading", Duration.ofMinutes(2));
    if (!Boolean.TRUE.equals(acquired)) {
        logger.info("Another instance is loading pending commands, skipping");
        return;  // Başka instance zaten yüklüyor
    }

    logger.info("Loading pending and executing commands from database into Redis on startup...");
    try {
        // 2. PENDING ve EXECUTING komutları DB'den yükle
        List<AppleCommand> pendingCommands = 
            appleCommandRepository.findPendingAndExecutingCommands();
        int loadedCount = 0;

        for (AppleCommand command : pendingCommands) {
            String udid = command.getAppleDeviceUdid();
            if (udid == null || udid.isEmpty()) {
                logger.warn("Skipping command {} - no device UDID", 
                    command.getCommandUUID());
                continue;
            }

            try {
                String commandXml = command.getTemplate();

                // 3. EXECUTING olanları PENDING'e AL (stale commands temizle)
                if (CommandStatus.EXECUTING.name()
                        .equals(command.getStatus())) {
                    logger.info("Resetting stale EXECUTING command {} for device {} " +
                        "back to PENDING", command.getCommandUUID(), udid);
                    String resetQuery = "UPDATE apple_command SET status = ?, " +
                        "execution_time = NULL WHERE command_uuid = ?";
                    jdbcTemplate.update(resetQuery, 
                        CommandStatus.PENDING.name(), command.getCommandUUID());
                }

                // 4. Stale inflight key'i temizle
                redisTemplate.delete(getInflightKey(udid));

                // 5. REDIS'E YÜKLEYİN
                String queueKey = getQueueKey(udid);
                RedisQueueItem item = new RedisQueueItem(command.getCommandUUID(), 
                    commandXml);
                redisTemplate.opsForList().rightPush(queueKey, 
                    serializeQueueItem(item));

                loadedCount++;
                logger.debug("Loaded command {} for device {}", 
                    command.getCommandUUID(), udid);
            } catch (Exception e) {
                logger.error("Failed to load command {} for device {}: {}",
                        command.getCommandUUID(), udid, e.getMessage());
            }
        }

        logger.info("Loaded {} pending/executing commands from database into Redis", 
            loadedCount);

        // 6. CİHAZLARA WAKE-UP GÖNDER
        Set<String> udidsWithPending = new HashSet<>();
        for (AppleCommand command : pendingCommands) {
            if (command.getAppleDeviceUdid() != null) {
                udidsWithPending.add(command.getAppleDeviceUdid());
            }
        }
        if (!udidsWithPending.isEmpty()) {
            logger.info("Sending wake-up push to {} devices with pending commands", 
                udidsWithPending.size());
            for (String udid : udidsWithPending) {
                try {
                    sendWakeUp(udid);
                } catch (Exception e) {
                    logger.warn("Failed to send startup wake-up to UDID {}: {}", 
                        udid, e.getMessage());
                }
            }
        }
    } catch (Exception e) {
        logger.error("Failed to load pending commands from database: {}", 
            e.getMessage(), e);
    }

    // 7. REAPER BAŞLAT (timeout temizleme)
    scheduler.scheduleAtFixedRate(this::expireStaleInFlightCommands, 30, 30, 
        TimeUnit.SECONDS);
    logger.info("Redis in-flight command reaper started (TTL: {} minutes, " +
        "check interval: 30s)", IN_FLIGHT_TTL.toMinutes());
}
```

**Adımlar:**
1. Başlangıç lock (multi-instance koordinasyonu)
2. DB'den PENDING + EXECUTING komutları yükle
3. Stale EXECUTING komutlarını PENDING'e al
4. Redis'e yükle
5. Wake-up push gönder
6. Reaper başlat

---

### d3) Komutlar hem Redis'e hem veritabanına yazılıyor — sebebi?

#### Detaylı Cevap:

Bu **Dual-Write Pattern** iki amaçla kullanılır:

| Hedef | Sebep | Faydası |
|-------|-------|---------|
| **PostgreSQL (DB)** | Persistence | Sunucu çökse bile veriler kaybolmaz; disk'te kalır |
| **Redis (Cache)** | Performance | Hızlı okuma; MS'lik latency ile komut verilir |

**Avantajlar:**
1. **High Availability**: Bir sistem başarısız olsa öbürü çalışır
   - Redis çökse: DB'den restore
   - DB çökse: Redis'ten çalışmaya devam
2. **Durability**: Komutlar permanent şekilde kaydedilir
   - DB kesintisi olmaksızın komut kaydı
3. **Recovery**: Restart sırasında DB'den Redis'e restore edebilir
4. **Audit Trail**: DB'de tüm komut geçmişi izlenebilir

#### Kod Konumu:
[src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L435-L475) ve [AppleCommand.java domain entity]

```java
// 1. VERITABANINA YAZ
appleCommandRepository.save(appleCommand);  // JPA persist

// 2. REDIS'E YAZ
String queueKey = QUEUE_KEY_PREFIX + udid;
RedisQueueItem item = new RedisQueueItem(command.getCommandUUID(), commandXml);
redisTemplate.opsForList().rightPush(queueKey, serializeQueueItem(item));

// 3. APNs'E GÖNDER
applePushService.sendMdmWakeUp(device.getToken(), device.getPushMagic());
```

#### Alternatif ve Değişiklik:

**Event Sourcing / Message Queue (Kafka/RabbitMQ):**
- Daha modern ve asenkron yaklaşım
- Dosya: [RedisAppleCommandQueueServiceImpl.java](src/main/java/com/arcyintel/arcops/apple_mdm/services/apple/command/RedisAppleCommandQueueServiceImpl.java#L435-L445)

```java
// Mevcut (Dual-write: senkron DB + Redis):
appleCommandRepository.save(appleCommand);  // DB WRITE
redisTemplate.opsForList().rightPush(queueKey, item);  // Redis WRITE

// Alternatif (Event-driven: Kafka):
// 1. appleCommandRepository.save(appleCommand);  // DB ilk
// 2. kafkaTemplate.send("apple-commands", appleCommand);  // Event pub
// 3. Kafka Listener (başka servis):
//    a) AppleCommand read (DB'den)
//    b) Redis queue'na ekle
//    c) APNs wake-up gönder
// Avantajlar:
// - Asenkron → daha hızlı response
// - Event trail → mesaj history
// - Ölçeklenebilir → multi-consumer
// - Failures izoleli → retry mekanizması
```

---

## Özet Tablo

| Soru | Çözüm | Sebep | İmplementasyon |
|------|-------|-------|-----------------|
| **a1) Veri Yapısı** | **QUEUE (FIFO)** | Komutlar sırasıyla yapılmalı | Redis List (RPUSH/LPOP) |
| **a2) Depolama** | **Dual-Write (DB+Redis)** | Durability + Performance | appleCommandRepository + Redis |
| **a3) Koruma Restart** | **loadPendingCommandsFromDatabase()** | @PostConstruct'ta yeniden yükleme | 7 adımlı recovery flow |
| **b1) Durumlar** | **5 state: PENDING, EXECUTING, COMPLETED, FAILED, CANCELED** | Lifecycle tracking | CommandStatus enum |
| **b2) Sırasıyla İletim** | ✅ (in-flight mekanizması) | Cihaz kapasitesi, state tracking | inflightKey ile 1 komut limit |
| **b3) Timeout** | **5 dakika + reaper (30sn)** | Stale commands temizleme | expireStaleInFlightCommands() |
| **c1) Race Condition Riski** | ✅ (Multi-instance'da mevcut) | 2 sunucu aynı komutu çalıştırabilir | Distributed lock zorunlu |
| **c2) Çözüm** | **Distributed Lock + Lua Scripts** | Atomic operations | SET IF ABSENT + RELEASE_LOCK_SCRIPT |
| **c3) Lock Implementation** | **Per-device key + TTL (10sn)** | Deadlock koruması | apple:command:lock:{udid} |
| **d1) Kayıp Riski** | ❌ (yok) | DB persistent | DB + Redis dual-write |
| **d2) Recovery Mekanizması** | ✅ (@PostConstruct methodu) | Startup'ta DB restore | 7 adımlı recovery |
| **d3) Dual-Write Sebebi** | **Durability + Speed** | Trade-off optimization | DB (persistent) + Redis (fast) |

---

## Dosya Referans Özeti

### Incelenmesi Gereken Ana Dosyalar:

| Dosya | Konum | Amaç |
|-------|-------|------|
| **RedisAppleCommandQueueServiceImpl** | `services/apple/command/` | Komut kuyruk yönetimi (Queue, Lock, Timeout) |
| **AppleCommand** | `domains/` | Komut entity (status, timestamps, failureReason) |
| **AppleCheckinServiceImpl** | `services/enrollment/` | Device check-in, Redis temizleme |
| **AgentPresenceService** | `services/agent/` | Cihaz online/offline durumu |
| **AppleCommandHandlerServiceImpl** | `services/apple/command/` | Komut yanıtları işleme |

### Key Redis Patterns:

```
apple:command:queue:{udid}          → Redis List (FIFO)
apple:command:inflight:{udid}       → String (komut UUID)
apple:command:lock:{udid}           → String (distributed lock, TTL 10s)
apple:command:reaper:lock           → String (reaper distributed lock, TTL 25s)
apple:command:startup-load-lock     → String (multi-instance startup lock)
```

### Startup Flow:

```
1. Spring Boot başlatılır
2. @PostConstruct: loadPendingCommandsFromDatabase()
   → DB'den PENDING/EXECUTING komutlar yüklenir
   → EXECUTING → PENDING reset
   → Redis queue'ye ekle
   → Wake-up push gönder
3. Scheduled Task başlatılır: expireStaleInFlightCommands() (30s periyot)
4. System hazır
```

### Normal Komut Flow:

```
1. Admin: POST /devices/{udid}/commands → Komut oluştur
2. pushCommand() → DB save + Redis RPUSH
3. Cihaz polling: PUT /mdm/connect
4. popCommand() → Distributed lock → Redis LINDEX 0 → In-flight SET
5. Cihaz: Komut işle
6. Cihaz: PUT /mdm/connect (yanıt gönder)
7. handleDeviceResponse() → COMPLETED/FAILED → Redis LPOP → In-flight DELETE
8. Kuyrukta komut varsa → Wake-up (repeat 3-7)
```
