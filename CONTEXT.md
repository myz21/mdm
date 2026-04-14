# Apple MDM Servisi - Detayli Proje Dokumantasyonu

## Genel Bakis

Apple MDM (Mobile Device Management) servisi, Apple cihazlarinin kurumsal ortamda yonetilmesini saglayan bir Spring Boot uygulamasidir.

| Ozellik | Deger |
|---|---|
| Framework | Spring Boot 3.4.5 |
| Java Surumu | 23 |
| Port | 8085 |
| Context Path | `/api/apple` |
| Artifact | `com.arcyintel.arcops:apple_mdm:0.0.1` |
| Veritabani | PostgreSQL (localhost:5433, schema: `apple_mdm`, DB: `molsec_uconos`) |
| Cache / Kuyruk | Redis (localhost:6379) |
| Mesajlasma | RabbitMQ (localhost:5673, admin/admin) |
| MQTT Broker | tcp://localhost:1883 |
| Service Discovery | Consul (localhost:8500) |
| API Dokumantasyonu | SpringDoc OpenAPI (springdoc-openapi-starter-webmvc-ui 2.3.0) |
| Host URL | `https://test.uconos.com/api/apple` |

### Temel Yetenekler
- Apple MDM protokolu uzerinden cihaz kaydi (DEP/ABM, BYOD, Profil tabanli)
- MDM komut kuyrugu ve cihaz yonetimi (kilit, silme, uygulama yukle/kaldir, profil yukle/kaldir vb.)
- Policy (politika) tabanli yapilandirma ve uyumluluk takibi
- Apple Business Manager (ABM) entegrasyonu: DEP cihaz yonetimi ve VPP uygulama dagitimi
- SCEP sertifika servisi
- APNs (Apple Push Notification service) entegrasyonu
- MQTT uzerinden agent (iOS/macOS istemci uygulamasi) iletisimi: telemetri, konum, presence
- RabbitMQ uzerinden mikroservisler arasi event iletisimi (back_core ile)
- Declarative Management destegi (hesap yapilari, yazilim guncelleme, Apple Watch kaydi)
- Enterprise uygulama yukleme (.ipa/.pkg) ve manifest olusturma

### Bagimliliklari
- `com.arcyintel.arcops:commons:0.0.1` - Ortak event sinif ve sabitleri
- Spring Cloud Consul Discovery
- Pushy (APNs istemcisi)
- dd-plist (Apple plist parse/serialize)
- BouncyCastle (SCEP ve sertifika islemleri)
- MapStruct (DTO donusumleri)
- Lombok
- Hypersistence Utils (Hibernate JSON destegi)
- Spring Integration MQTT (Eclipse Paho)

---

## Dosya Yapisi

```
apple_mdm/
  pom.xml
  mvnw, mvnw.cmd
  certs/                                  # Apple sertifikalari (PEM, CER, P12, key)
  docs/
  src/
    main/
      java/com/arcyintel/arcops/apple_mdm/
        AppleMdmApplication.java          # Ana Spring Boot sinifi

        clients/
          BackCoreClient.java             # back_core REST istemcisi

        configs/
          audit/
            AuditorAwareImpl.java         # JPA audit (createdBy, modifiedBy)
            JpaConfig.java
          enrollment/
            AccountDrivenEnrollmentProperties.java  # ADDE yapilandirmasi
          event/
            RabbitMQConfig.java           # RabbitMQ exchange/queue tanimlari
          http/
            HttpBeanConfig.java           # RestTemplate vb. bean'ler
          mqtt/
            MqttConfig.java              # MQTT inbound/outbound adapter
            MqttProperties.java          # MQTT yapilandirma properties
          openapi/
            OpenApiConfig.java           # Swagger/OpenAPI yapilandirmasi
            SwaggerExampleCustomizer.java
          redis/
            RedisConfig.java             # RedisTemplate yapilandirmasi
          security/
            ScepConfig.java              # SCEP CA sertifika/key yukleme
            SecurityConfig.java          # Spring Security (JWT + MDM bypass)

        controllers/
          AbmController.java                      # ABM DEP + VPP islemleri
          AccountDrivenEnrollmentController.java  # BYOD/ADDE enrollment
          AgentAuthController.java                # Agent kimlik dogrulama
          AgentDeviceController.java              # Agent cihaz yonetimi
          AppGroupController.java                 # Uygulama grubu CRUD
          AppleAccountController.java             # Hesap CRUD + cihaz atama
          AppleDeviceController.java              # Cihaz detay sorgulama
          CertificationController.java            # Sertifika/token yukleme
          DashboardController.java                # Dashboard istatistikleri
          DeviceCommandController.java            # MDM komut gonderme
          EnrollmentController.java               # Enrollment wizard durumu
          EnterpriseAppController.java            # Enterprise uygulama CRUD
          ItunesAppController.java                # iTunes/VPP uygulama sorgulama
          MdmCheckInController.java               # MDM checkin (Authenticate, TokenUpdate, CheckOut)
          MdmConnectController.java               # MDM connect (komut polling)
          MdmEnrollmentProfileController.java     # Enrollment profili olusturma/indirme
          MdmEnrollmentWebAuthController.java     # Web tabanli enrollment auth (DEP + BYOD)
          AgentNotificationController.java         # Agent APNs push notification gonderme
          ScepController.java                     # SCEP sertifika islemleri
          ScreenShareController.java              # Ekran paylasimi start/stop/status (online/offline wake-up)
          RemoteTerminalController.java           # Uzak terminal oturum start/stop/status (offline wake-up destegi)
          RemoteControlController.java            # Uzak kontrol mouse/keyboard event iletimi (MQTT uzerinden)
          ServiceDiscoveryController.java         # .well-known/com.apple.remotemanagement
          AppCatalogController.java               # App catalog atama/sorgulama (admin API)
          AgentCatalogController.java             # Agent app catalog (enriched catalog + install)
          DeviceAuthHistoryController.java        # Cihaz auth gecmisi sorgulama

        domains/                          # JPA Entity'ler (base class'lar commons projesinde)
          AbmDevice.java
          AbmProfile.java
          AgentLocation.java
          AgentPresenceHistory.java
          AgentTelemetry.java
          AppGroup.java
          AppGroupItem.java
          AppleAccount.java
          AppleCommand.java
          AppleDevice.java
          AppleDeviceApp.java
          AppleDeviceInformation.java
          AppleDeviceLocation.java
          AppleIdentity.java
          EnrollmentAuditLog.java
          EnrollmentHistory.java
          EnrollmentProfileType.java      # Enum: DEVICE, USER_ENROLLMENT, ACCOUNT_DRIVEN_DEVICE
          EnrollmentStatus.java
          EnrollmentType.java             # Enum: DEP, USER_ENROLLMENT, PROFILE, UNKNOWN
          EnterpriseApp.java
          ItunesAppMeta.java
          OAuth2ProviderConfig.java       # POJO (Redis cache, JPA entity degil)
          Policy.java
          DeviceAuthHistory.java          # Agent login/logout gecmisi
          AppCatalogAssignment.java       # AppGroup → Account/AccountGroup atama

        event/
          listener/
            ApplePolicyApplyListener.java       # Policy uygulama (RabbitMQ)
            ApplePolicyCreatedListener.java     # Policy olusturma senkronizasyonu
            ApplePolicyDeletedListener.java     # Policy silme
            ApplePolicyUpdatedListener.java     # Policy guncelleme
            PolicyEventHelper.java              # Ortak policy conversion/validation
            IdentitySyncEventListener.java      # Identity senkronizasyonu (back_core'dan)
            AccountSyncEventListener.java      # Account senkronizasyonu (back_core'dan, ayni UUID)
          publisher/
            AccountEventPublisher.java          # AccountCreatedEvent
            DeviceEventPublisher.java           # DeviceEnrolledEvent, DeviceDisenrolledEvent, DevicePresenceChangedEvent
            PolicyEventPublisher.java           # PolicyApplicationFailedEvent

        models/
          api/
            account/
              CreateAppleAccountDto.java, GetAppleAccountDto.java, UpdateAppleAccountDto.java
            agent/
              AgentAuthRequest.java, AgentAuthResponse.java
            appgroup/
              AppGroupDto.java, AppGroupItemDto.java, AppGroupUpsertDto.java
            appresolve/
              AppResolveRequest.java, AppResolveResponseItem.java
            dashboard/
              DashboardStatsDto.java
            device/
              AbmDeviceSummaryDto.java, GetAppleDeviceDetailDto.java
            enterpriseapp/
              GetEnterpriseAppDto.java
            enrollment/
              AuthenticatedUser.java, EnrollmentAuditLogDto.java,
              GetEnrollmentStatusDto.java, SendEnrollmentEmailDto.java
            itunesapp/
              GetItunesAppDto.java
            systemsetting/
              SystemSettingDto.java
          cert/
            abm/
              ClearProfileRequest.java, ClearProfileResponse.java, Device.java,
              DeviceListRequest.java, DeviceStatusResponse.java,
              FetchDevicesResponse.java, Profile.java, ProfileResponse.java,
              ServerToken.java
            vpp/
              asset/
                AssetAssignRequest.java, Assignment.java, AssignmentResponse.java,
                ItunesLookupResponse.java, VppAsset.java, VppAssetsResponse.java
              user/
                VppUser.java, VppUsersResponse.java
          enums/
            CheckInTypes.java             # Authenticate, TokenUpdate, CheckOut, DeclarativeManagement
            CommandTypes.java             # Tum MDM komut turleri
            PayloadIdentifiers.java       # Payload identifier sabitleri
          profile/                        # (eski: profilePayloads)
            BasePayload.java, DnsProxy.java, Font.java, HomeScreenLayout.java,
            Notification.java, Passcode.java, PerAppVpn.java, Profile.java,
            ProfileRemovalPassword.java, Scep.java, Settings.java, Vpn.java,
            PolicyContext.java
            restrictions/
              WatchosRestrictions.java
          session/
            ScreenShareSession.java       # Redis oturum modeli
            RemoteTerminalSession.java    # Redis oturum modeli (PENDING/ACTIVE/ENDED)
            VncSession.java               # VNC oturum modeli

        repositories/                     # Spring Data JPA Repository'leri
          AbmDeviceRepository.java, AbmProfileRepository.java,
          AgentLocationRepository.java, AgentPresenceHistoryRepository.java,
          AgentTelemetryRepository.java, AppGroupRepository.java,
          AppleAccountRepository.java, AppleCommandRepository.java,
          AppleDeviceAppRepository.java, AppleDeviceInformationRepository.java,
          AppleDeviceLocationRepository.java, AppleDeviceRepository.java,
          AppleIdentityRepository.java, EnrollmentAuditLogRepository.java,
          EnrollmentHistoryRepository.java, EnrollmentStatusRepository.java,
          EnterpriseAppRepository.java, ItunesAppMetaRepository.java,
          PolicyRepository.java, DeviceAuthHistoryRepository.java,
          AppCatalogAssignmentRepository.java

        services/                         # Service interface + impl (managers/ buraya tasinmis)
          account/
            AppleAccountService.java, AppleAccountServiceImpl.java
          agent/
            AgentAuthService.java, AgentAuthServiceImpl.java
            AgentCatalogService.java, AgentCatalogServiceImpl.java
            AgentDeviceDataService.java, AgentDeviceDataServiceImpl.java
            AgentLocationService.java, AgentTelemetryService.java, ...
          app/
            AppGroupService.java, AppGroupServiceImpl.java
            AppResolveService.java, AppResolveServiceImpl.java
            EnterpriseAppService.java, EnterpriseAppServiceImpl.java
            ItunesAppService.java, ItunesAppServiceImpl.java
          apple/
            abm/
              AppleAbmTokenService.java, AppleAbmTokenServiceImpl.java
              AppleDepService.java, AppleDepServiceImpl.java
              AppleVppService.java, AppleVppServiceImpl.java
            apns/
              AgentPushService.java
              ApplePushService.java, ApplePushServiceImpl.java
              PendingNotificationService.java
            cert/
              AppleCertificationService.java, AppleCertificationServiceImpl.java
              ApplePushCredentialService.java, ApplePushCredentialServiceImpl.java
              AppleScepService.java, AppleScepServiceImpl.java
            command/
              AppleCommandBuilderService.java, AppleCommandBuilderServiceImpl.java
              AppleCommandHandlerService.java, AppleCommandHandlerServiceImpl.java
              AppleCommandSenderService.java, AppleCommandSenderServiceImpl.java
              InMemoryAppleCommandQueueServiceImpl.java
              RedisAppleCommandQueueServiceImpl.java
              PolicyComplianceData.java, PolicyComplianceTracker.java
              strategy/
                AbstractPlatformPayloadStrategy.java
                IosPolicyPayloadStrategy.java, IpadosPolicyPayloadStrategy.java
                MacosPolicyPayloadStrategy.java, TvosPolicyPayloadStrategy.java
                VisionosPolicyPayloadStrategy.java, WatchosPolicyPayloadStrategy.java
                PlatformPayloadStrategy.java
            policy/
              ApplePolicyApplicationService.java, ApplePolicyApplicationServiceImpl.java
          dashboard/
            DashboardStatsService.java
          device/
            AppleDeviceDetailService.java, AppleDeviceDetailServiceImpl.java
            DeviceAuthHistoryService.java, DeviceAuthHistoryServiceImpl.java
            DeviceLookupService.java
          email/
            EnrollmentEmailService.java, EnrollmentEmailServiceImpl.java
          enrollment/
            AccountDrivenEnrollmentService.java, AccountDrivenEnrollmentServiceImpl.java
            AppleCheckinService.java, AppleCheckinServiceImpl.java
            AppleEnrollmentService.java, AppleEnrollmentServiceImpl.java
            AppleEnrollmentWebAuthService.java, AppleEnrollmentWebAuthServiceImpl.java
            EnrollmentAuditService.java, EnrollmentAuditServiceImpl.java
            EnrollmentProfileGenerator.java, EnrollmentProfileGeneratorImpl.java
            EnrollmentStatusService.java, EnrollmentStatusServiceImpl.java
            ManagedAppleIdAccountResolver.java
            OAuth2AuthStrategy.java, OAuth2ProviderConfigCacheService.java
            SimpleAuthStrategy.java
          mappers/
            AbmDeviceMapper.java, AppleAccountMapper.java
            AppGroupMapper.java, EnrollmentAuditLogMapper.java
            EnrollmentStatusMapper.java, EnterpriseAppMapper.java
            ItunesAppMapper.java
          mqtt/
            MqttMessageRouter.java        # Topic bazli mesaj yonlendirme (configs'ten tasinmis)
          screenshare/
            ScreenShareSessionService.java
            ScreenShareSignalingHandler.java
            PendingScreenShareService.java
          terminal/
            RemoteTerminalSessionService.java
            RemoteTerminalHandler.java
            PendingTerminalService.java

        specifications/                   # JPA Specification'lar (dinamik filtreleme)
          AbstractFilterSpecification.java      # Template Method base class
          AppGroupFilterSpecification.java      # extends base, subquery platform handler
          EnterpriseAppFilterSpecification.java # extends base, ElementCollection platform
          ItunesAppFilterSpecification.java     # extends base, ElementCollection platform

        utils/
          HttpRequestUtils.java           # IP/UA extraction (statik utility)
          JsonNodeUtils.java              # JSON node helper'lar (statik utility)
          certs/
            PemUtil.java                  # PEM dosya yardimcilari
          storage/                        # (eski: fileOperations)
            FileSystemStorageService.java # Dosya depolama (enterprise app binary)
            RenameMultipartFile.java
            StorageException.java
            StorageFileNotFoundException.java
            StorageService.java

      resources/
        application.yaml                  # Ana yapilandirma dosyasi
        templates/
          enrollment-login.html           # Web auth login sayfasi (DEP + BYOD)
        db/migration/
          V1__Initial_Script.sql          # Cekirdek MDM tablolari (19 tablo)
          V2__Add_Identity.sql            # apple_identity tablosu
          V3__.sql                        # (bos veya kucuk duzeltme)
          V4__Add_Abm_Device_Status.sql   # ABM cihaz durum alanlari
          V5__Add_Enrollment_History.sql   # enrollment_history tablosu
          V6__Add_Agent_Presence.sql       # agent_presence_history tablosu
          V7__Add_Agent_Telemetry_Location.sql  # agent_telemetry + agent_location tablolari
          V8__Presence_History_Event_Type.sql       # agent_presence_history olay tipi kolonu
          V9__Add_Wifi_SSID_To_Telemetry.sql      # agent_telemetry tablosuna wifi_ssid kolonu
          V10__Add_Agent_Push_Token.sql            # apple_device tablosuna agent_push_token kolonu
          V11__Add_Location_Source.sql             # agent_location tablosuna source kolonu
          V12__Add_Device_Auth_History.sql         # device_auth_history tablosu
          V13__Add_App_Catalog_Assignment.sql      # app_catalog_assignment tablosu

    test/
      java/com/arcyintel/arcops/apple_mdm/
        AppleMdmApplicationTests.java
```

---

## Entity'ler

### AppleDevice
Ana cihaz entity'si. Soft delete destekli (`status = 'DELETED'`).

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar (AbstractEntity'den) |
| udid | String (unique) | Apple Unique Device Identifier |
| enrollmentId | String(128) | User Enrollment icin anonim kimlik (UDID yerine) |
| enrollmentUserId | String(256) | Apple'dan gelen kullanici kimligi |
| enrollmentType | EnrollmentType (enum) | DEP, USER_ENROLLMENT, PROFILE, UNKNOWN |
| isUserEnrollment | Boolean | BYOD kayit mi? |
| serialNumber | String | Seri numarasi |
| productName | String | Urun adi (iPhone15,2 vb.) |
| buildVersion | String | iOS build surumu |
| osVersion | String | iOS surum numarasi |
| token | String(2048) | APNs device token (Base64) |
| pushMagic | String | APNs push magic string |
| unlockToken | String(4096) | Cihaz kilidi acma token'i |
| isDeclarativeManagementEnabled | Boolean | DDM destegi var mi? |
| declarativeStatus | JSON (Map) | DDM durum bilgisi |
| declarationToken | String(248) | Mevcut deklaratif token |
| appliedPolicy | JSON (Map) | Uygulanan politika JSON'u |
| isCompliant | Boolean | Uyumluluk durumu |
| complianceFailures | JSON (Map) | Uyumluluk hatasi detaylari |
| status | String | ACTIVE, DELETED |
| managementMode | String | Yonetim modu |
| agentOnline | Boolean | Agent cevrimici mi? |
| agentLastSeenAt | Instant | Agent son gorunme zamani |
| agentVersion | String(50) | Agent surum bilgisi |
| agentPlatform | String(20) | Agent platformu (ios/macos) |
| agentPushToken | String | APNs push token (agent alert/silent push icin, V10 migration) |
| deviceProperties | AppleDeviceInformation (1:1) | Detayli cihaz bilgileri |
| appleCommands | List\<AppleCommand\> (1:N) | Komut gecmisi |
| accounts | Set\<AppleAccount\> (N:M) | Atanmis hesaplar |
| installedApps | List\<AppleDeviceApp\> (1:N) | Yuklu uygulamalar |

### AppleAccount
Kullanici hesabi entity'si. Soft delete destekli.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| username | String (zorunlu) | Kullanici adi |
| email | String | E-posta adresi |
| managedAppleId | String | Managed Apple ID |
| fullName | String | Tam ad |
| status | String | ACTIVE, DELETED |
| identity | AppleIdentity (N:1) | Bagli kimlik |
| devices | Set\<AppleDevice\> (N:M) | Atanmis cihazlar (`apple_account_devices` ara tablosu) |

### AppleIdentity
Kimlik entity'si. back_core'dan RabbitMQ ile senkronize edilir. Agent kimlik dogrulama ve BYOD enrollment icin kullanilir.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| username | String (zorunlu) | Kullanici adi |
| email | String | E-posta adresi |
| fullName | String | Tam ad |
| source | String (zorunlu) | Kaynak (MANUAL, GOOGLE_WORKSPACE, AZURE_ENTRA vb.) |
| externalId | String | Harici sistem kimligi |
| passwordHash | String | BCrypt hash'lenmis sifre |
| status | String | ACTIVE, DELETED |
| accounts | Set\<AppleAccount\> (1:N) | Bagli hesaplar |

### AppleCommand
MDM komut entity'si.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| commandUUID | String | Komut benzersiz kimligi (`{udid}_{commandType}_{random}`) |
| commandType | String | Komut turu (InstallProfile, DeviceInformation vb.) |
| template | TEXT | Komut XML plist sablonu |
| status | String | PENDING, EXECUTING, COMPLETED, FAILED, CANCELED |
| requestTime | Instant | Olusturulma zamani |
| executionTime | Instant | Cihaza gonderilme zamani |
| completionTime | Instant | Tamamlanma/hata zamani |
| failureReason | String(1024) | Hata nedeni |
| appleDeviceUdid | String | Cihaz UDID (FK) |
| appleDevice | AppleDevice (N:1) | Bagli cihaz |
| policyId | UUID | Politika kimligi (FK, nullable) |
| policy | Policy (N:1) | Bagli politika |

### AppleDeviceInformation
Cihaz detay bilgileri (DeviceInformation MDM komutu yaniti). AppleDevice ile 1:1 iliski.

| Alan | Tip | Aciklama |
|---|---|---|
| deviceName | String | Cihaz adi |
| modelName | String | Model adi |
| productName | String | Urun adi |
| udid | String | UDID |
| imei | String | IMEI numarasi |
| meid | String | MEID numarasi |
| osVersion | String | Isletim sistemi surumu |
| buildVersion | String | Build surumu |
| batteryLevel | Number | Batarya seviyesi |
| deviceCapacity | Number | Depolama kapasitesi |
| supervised | Boolean | Denetimli mi? |
| activationLockEnabled | Boolean | Aktivasyon kilidi acik mi? |
| bluetoothMAC | String | Bluetooth MAC adresi |
| wifiMAC | String | Wi-Fi MAC adresi |
| model | String | Model kodu |
| securityInfo | JSON (Map) | Guvenlik bilgileri |
| certificateList | JSON (List\<Map\>) | Yuklu sertifika listesi |
| mdmOptions | JSON (Map) | MDM opsiyonlari |
| serviceSubscriptions | JSON (Map) | Hucresel abonelik bilgileri |
| ... | ... | +20 alan daha (cellular, roaming, hotspot, diagnostics vb.) |

### AppleDeviceApp
Cihazda yuklu uygulamalar.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| bundleIdentifier | String (zorunlu) | Uygulama bundle ID (com.example.app) |
| name | String | Uygulama adi |
| version | String | Surum |
| shortVersion | String | Kisa surum |
| bundleSize | Integer | Boyut (byte) |
| installing | boolean | Yukleniyor mu? |
| managed | boolean | MDM tarafindan yonetiliyor mu? |
| hasConfiguration | boolean | Yapilandirmasi var mi? |
| hasFeedback | boolean | Geri bildirimi var mi? |
| validated | boolean | Dogrulanmis mi? |
| managementFlags | int | Yonetim bayraklari |
| appleDevice | AppleDevice (N:1) | Bagli cihaz |

### Policy
Politika entity'si. back_core'dan RabbitMQ ile senkronize edilir. Soft delete destekli (`status = 'ACTIVE'` filtresi).

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar (back_core'dan gelir) |
| name | String (zorunlu) | Politika adi |
| platform | String (zorunlu) | Hedef platform (ios, macos, vb.) |
| payload | JSON (Map) | Politika icerigi (kisitlamalar, yapilandirmalar vb.) |
| kioskLockdown | JSON (Map) | Kiosk/tekli uygulama kilidi ayarlari |
| status | String | ACTIVE, DELETED |
| creationDate | Date | Olusturma tarihi |
| lastModifiedDate | Date | Son degisiklik tarihi |

### AppGroup
Uygulama grubu. VPP ve Enterprise uygulamalari bir arada gruplar.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| name | String (unique, zorunlu) | Grup adi |
| description | String(1024) | Aciklama |
| metadata | JSON (Map) | Ek meta veri |
| items | List\<AppGroupItemEntity\> (1:N) | Gruptaki uygulamalar (sirali) |

### AppGroupItemEntity
Uygulama grubu ogesi.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| appGroup | AppGroup (N:1) | Ait oldugu grup |
| appType | AppGroup.AppType (enum) | VPP veya ENTERPRISE |
| trackId | String(64) | iTunes Track ID (VPP icin) |
| bundleId | String(512) | Bundle ID |
| artifactRef | String(1024) | Enterprise artifact referansi |
| displayName | String(512) | Gorunen ad |
| supportedPlatforms | List\<String\> (transient) | Desteklenen platformlar |
| iconUrl | String (transient) | Ikon URL'si |

### EnterpriseApp
Kurumsal uygulama (.ipa/.pkg) entity'si.
**2 asamali upload (17 Mart 2026):**
- Phase 1: `POST /upload-analyze` → temp'e kaydet, metadata parse et (IPA: Info.plist, PKG: xar/Distribution), `AppAnalyzeResponse` don
- Phase 2: `POST /confirm/{tempId}` → kullanici metadata'yi inceleyip onaylar, kalici storage'a tasinir
- Cancel: `DELETE /cancel/{tempId}` → temp sil
- `TempAppStorage`: temp dosya yonetimi + saatlik cleanup (1 saat eski dosyalar silinir)
- Legacy `POST /upload` backward compatibility icin korunuyor

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| bundleId | String(512, zorunlu) | Bundle ID |
| version | String(64) | Surum (bundleId+version unique) |
| buildVersion | String(64) | Build surumu |
| displayName | String(512) | Gorunen ad |
| minimumOsVersion | String(32) | Minimum OS surumu |
| fileSizeBytes | Long | Dosya boyutu |
| fileName | String(512) | Dosya adi |
| storagePath | String(1024, zorunlu) | Depolama yolu |
| fileHash | String(128) | Dosya hash'i |
| platform | String(32) | Platform |
| iconBase64 | TEXT | Base64 kodlanmis ikon |
| supportedPlatforms | List\<String\> | Desteklenen platformlar (ayri tablo) |

### ItunesAppMeta
iTunes/VPP uygulama metadata'si. Apple VPP API'den senkronize edilir.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| trackId | Long (unique, zorunlu) | Apple Track ID |
| trackName | String | Uygulama adi |
| bundleId | String | Bundle ID |
| version | String | Surum |
| description | TEXT(20KB) | Aciklama |
| price | Double | Fiyat |
| primaryGenreName | String | Tur |
| averageUserRating | Double | Ortalama puan |
| artworkUrl512, artworkUrl100, artworkUrl60 | String(1024) | Ikon URL'leri |
| minimumOsVersion | String | Minimum OS surumu |
| isVppDeviceBasedLicensingEnabled | Boolean | Cihaz tabanli VPP lisansi |
| totalCount, assignedCount, availableCount, retiredCount | int | VPP lisans sayilari |
| supportedPlatforms | List\<String\> | Desteklenen platformlar (ayri tablo) |
| ... | ... | +15 alan daha (seller, rating, contentAdvisory vb.) |

### AbmDevice
Apple Business Manager cihazi.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| serialNumber | String (unique, zorunlu) | Seri numarasi |
| model | String | Model |
| description | String | Aciklama |
| color | String | Renk |
| assetTag | String | Varlik etiketi |
| os | String | Isletim sistemi |
| deviceFamily | String | Cihaz ailesi |
| profile | AbmProfile (N:1) | Atanmis profil |
| profileStatus | String | pushed, assigned, empty |
| profileAssignTime | String | Profil atama zamani |
| profilePushTime | String | Profil gonderme zamani |
| deviceAssignedDate | String | Cihaz atanma tarihi |
| deviceAssignedBy | String | Atayan kisi |
| abmStatus | String | ACTIVE (varsayilan) |

### AbmProfile
ABM enrollment profili.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| profileUuid | String (unique, zorunlu) | Apple profil UUID'si |
| profileName | String | Profil adi |
| url | String | Profil URL'si |
| configurationWebUrl | String | Web yapilandirma URL'si |
| allowPairing | Boolean | Eslestirmeye izin ver |
| isSupervised | Boolean | Denetimli mod |
| isMultiUser | Boolean | Coklu kullanici |
| isMandatory | Boolean | Zorunlu profil |
| awaitDeviceConfigured | Boolean | Yapilandirma bekle |
| isMdmRemovable | Boolean | MDM kaldirilabilir mi? |
| autoAdvanceSetup | Boolean | Otomatik kurulum ilerleme |
| orgMagic | String | Organizasyon magic string |
| skipSetupItems | JSON (List\<String\>) | Atlanan kurulum adimlari |
| anchorCerts | JSON (List\<String\>) | Guven sertifikalari |
| supervisingHostCerts | JSON (List\<String\>) | Denetim sertifikalari |
| department, language, region | String | Departman, dil, bolge |
| supportPhoneNumber, supportEmailAddress | String | Destek iletisim |

### EnrollmentStatus
Enrollment wizard durumu. Singleton entity (tek kayit).

| Alan | Tip | Aciklama |
|---|---|---|
| currentStep | Integer | Mevcut adim (1-6) |
| completedSteps | Integer[] | Tamamlanan adimlar |
| enrollmentCompleted | Boolean | Enrollment tamamlandi mi? |
| apnsCertUploaded | Boolean | APNs sertifikasi yuklendi mi? |
| apnsCertSubject, apnsCertIssuer, apnsCertSerial | String | APNs sertifika bilgileri |
| apnsCertNotBefore, apnsCertNotAfter | Date | APNs sertifika gecerlilik |
| apnsPushTopic | String(256) | Push topic |
| depTokenUploaded | Boolean | DEP token yuklendi mi? |
| depTokenConsumerKey | String(256) | DEP consumer key |
| depTokenNotAfter | Date | DEP token bitis |
| depOrgName, depOrgEmail, depOrgPhone, depOrgAddress | String | DEP organizasyon bilgileri |
| vppTokenUploaded | Boolean | VPP token yuklendi mi? |
| vppTokenNotAfter | Date | VPP token bitis |
| vppOrgName, vppLocationName | String | VPP organizasyon bilgileri |
| vendorCertNotAfter | Date | Vendor sertifika bitis |
| vendorCertRenewedAt | Date | Vendor sertifika yenilenme |
| pushCertRenewedAfterVendor | Boolean | Push sertifika vendor'dan sonra yenilendi mi? |

### EnrollmentHistory
Cihaz kayit gecmisi.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| deviceId | UUID | Cihaz kimligi |
| udid | String | UDID |
| enrollmentId | String(128) | User Enrollment ID |
| enrollmentUserId | String(256) | Enrollment kullanici kimligi |
| enrollmentType | String(50) | Kayit turu |
| isUserEnrollment | Boolean | BYOD mu? |
| serialNumber, productName, osVersion, buildVersion | String | Cihaz bilgileri |
| token | String(2048) | APNs token |
| pushMagic | String | Push magic |
| status | String(50) | Kayit durumu |
| enrolledAt | LocalDateTime | Kayit zamani |
| unenrolledAt | LocalDateTime | Kayit iptali zamani |
| unenrollReason | String(50) | Iptal nedeni |
| accountId | UUID | Hesap kimligi |

### EnrollmentAuditLog
Sertifika/token islemleri denetim logu.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| creationDate | Date | Olusturma tarihi |
| action | AuditAction (enum) | UPLOAD, RENEW, GENERATE, DELETE, REFRESH, HOT_RELOAD |
| targetType | AuditTargetType (enum) | APNS_CERT, DEP_TOKEN, VPP_TOKEN, VENDOR_CERT, DEP_CERT, ENROLLMENT_STATUS |
| status | AuditStatus (enum) | SUCCESS, FAILURE |
| message | String(1024) | Islem mesaji |
| details | TEXT | Detaylar (JSON) |
| errorMessage | String(1024) | Hata mesaji |
| performedBy | String(256) | Islemi yapan kisi |
| ipAddress | String(64) | IP adresi |
| userAgent | String(512) | Tarayici bilgisi |

### AgentTelemetry
Agent telemetri verileri. MQTT uzerinden alinir.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| device | AppleDevice (N:1) | Bagli cihaz |
| deviceIdentifier | String (zorunlu) | Cihaz tanimilayicisi |
| deviceCreatedAt | Instant (zorunlu) | Cihaz tarafinda olusturma zamani |
| serverReceivedAt | Instant (zorunlu) | Sunucu alma zamani |
| **Batarya** | | |
| batteryLevel | Integer | Batarya seviyesi (%) |
| batteryCharging | Boolean | Sarj oluyor mu? |
| batteryState | String(20) | Batarya durumu |
| lowPowerMode | Boolean | Dusuk guc modu |
| **Depolama** | | |
| storageTotalBytes | Long | Toplam depolama |
| storageFreeBytes | Long | Bos alan |
| storageUsedBytes | Long | Kullanilan alan |
| storageUsagePercent | Integer | Kullanim yuzdesi |
| **Bellek** | | |
| memoryTotalBytes | Long | Toplam bellek |
| memoryAvailableBytes | Long | Kullanilabilir bellek |
| **Sistem** | | |
| systemUptime | Integer | Calisma suresi (saniye) |
| cpuCores | Integer | CPU cekirdek sayisi |
| thermalState | String(20) | Termal durum |
| brightness | Integer | Ekran parlaklik |
| osVersion | String(50) | OS surumu |
| modelIdentifier | String(50) | Model tanimlayicisi |
| deviceModel | String(100) | Cihaz modeli |
| **Ag** | | |
| networkType | String(20) | Ag turu (wifi, cellular) |
| ipAddress | String(45) | IP adresi |
| isExpensive | Boolean | Pahali ag mi? |
| isConstrained | Boolean | Kisitli ag mi? |
| vpnActive | Boolean | VPN aktif mi? |
| carrierName | String(100) | Opertor adi |
| radioTechnology | String(20) | Radyo teknolojisi |
| **Guvenlik** | | |
| jailbreakDetected | Boolean | Jailbreak tespit edildi mi? |
| debuggerAttached | Boolean | Debugger bagli mi? |
| **Yerellesme** | | |
| localeLanguage | String(10) | Dil |
| localeRegion | String(10) | Bolge |
| localeTimezone | String(100) | Saat dilimi |

### AgentLocation
Agent konum verileri. MQTT uzerinden alinir.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| device | AppleDevice (N:1) | Bagli cihaz |
| deviceIdentifier | String (zorunlu) | Cihaz tanimlayicisi |
| deviceCreatedAt | Instant (zorunlu) | Cihaz tarafinda olusturma zamani |
| serverReceivedAt | Instant (zorunlu) | Sunucu alma zamani |
| latitude | Double (zorunlu) | Enlem |
| longitude | Double (zorunlu) | Boylam |
| altitude | Double | Yukseklik |
| horizontalAccuracy | Double | Yatay dogruluk (metre) |
| verticalAccuracy | Double | Dikey dogruluk (metre) |
| speed | Double | Hiz (m/s) |
| course | Double | Yon (derece) |
| floorLevel | Integer | Kat seviyesi |

### AgentPresenceHistory
Agent durum gecisi olay logu. Her kayit bir durum gecisini (ONLINE veya OFFLINE) temsil eder. 30sn heartbeat'ler yalnizca agent_last_seen_at'i guncelledigi icin DB sismesi onlenir; sadece gercek durum degisiklikleri kaydedilir.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| device | AppleDevice (N:1) | Bagli cihaz |
| deviceIdentifier | String (zorunlu) | Cihaz tanimlayicisi |
| eventType | String (zorunlu) | Durum gecisi turu (ONLINE, OFFLINE) |
| timestamp | Instant (zorunlu) | Olay zamani |
| durationSeconds | Long | Onceki durumun suresi (saniye) |
| agentVersion | String(50) | Agent surumu |
| agentPlatform | String(20) | Agent platformu |
| reason | String(100) | Gecis nedeni (OFFLINE olaylari icin: LWT, stale_cleanup, vb.) |

### AppleDeviceLocation
MDM Lost Mode uzerinden alinan cihaz konumu (agent degil, MDM komutu yaniti).

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Birincil anahtar |
| appleDevice | AppleDevice (N:1) | Bagli cihaz |
| latitude, longitude | Double | Enlem, boylam |
| altitude | Double | Yukseklik |
| speed | Double | Hiz |
| course | Double | Yon |
| horizontalAccuracy, verticalAccuracy | Double | Dogruluk |
| timestamp | Instant | Cihaz tarafindan bildirilen zaman |
| createdDate | Instant | Kayit zamani |

### OAuth2ProviderConfig
OAuth2 saglayici yapilandirmasi. **JPA entity degil** - back_core'dan REST ile alinir ve Redis'te onbellegge alinir.

| Alan | Tip | Aciklama |
|---|---|---|
| id | UUID | Kimlik |
| providerType | ProviderType (enum) | GOOGLE_WORKSPACE, AZURE_ENTRA, CUSTOM_OIDC |
| domain | String | Domain (orn. company.com) |
| clientId | String | OAuth2 client ID |
| clientSecret | String | OAuth2 client secret |
| authorizationUrl | String | Yetkilendirme URL'si |
| tokenUrl | String | Token URL'si |
| jwkSetUri | String | JWK seti URL'si |
| scopes | String | Kapsam (varsayilan: openid,profile,email) |
| enabled | Boolean | Etkin mi? |

---

## REST API Endpoint'leri

### MDM Protokol Endpoint'leri (Apple Cihaz Iletisimi)

| Yontem | Yol | Aciklama |
|---|---|---|
| POST/PUT | `/mdm/checkin` | MDM check-in (Authenticate, TokenUpdate, CheckOut, DeclarativeManagement) |
| GET | `/mdm/checkin/{deviceId}/{assetIdentifier}` | Declarative asset document getir |
| PUT | `/mdm/connect` | MDM connect - komut polling ve yanit isleme |

### Service Discovery & Account-Driven Enrollment

| Yontem | Yol | Aciklama |
|---|---|---|
| GET | `/.well-known/com.apple.remotemanagement` | ADDE/BYOD kesfetme endpoint'i |
| POST | `/mdm/account-enrollment/byod` | BYOD (User Enrollment) kimlik dogrulama |
| POST | `/mdm/account-enrollment/adde` | ADDE (Account-Driven Device Enrollment) kimlik dogrulama |

### Enrollment Profil & Web Auth

| Yontem | Yol | Aciklama |
|---|---|---|
| GET/POST | `/mdm/enrollment` | DEP enrollment profili indir |
| POST | `/mdm/enrollment/user` | BYOD User Enrollment profili olustur |
| POST | `/mdm/enrollment/adde` | ADDE profili olustur |
| POST | `/mdm/enrollment/generate` | Ture gore profil olustur |
| GET | `/mdm/enrollment/url` | QR kod icin enrollment URL'si |
| POST | `/mdm/enrollment/send-email` | Enrollment davet e-postasi gonder |
| GET | `/mdm/enrollment/web-auth` | Web auth login sayfasi (DEP + BYOD) |
| POST | `/mdm/enrollment/web-auth` | Web auth login formu gonder |
| POST | `/mdm/enrollment/web-auth/otp/send` | OTP kodu gonder |
| POST | `/mdm/enrollment/web-auth/otp/verify` | OTP dogrula ve kaydet |

### Enrollment Wizard

| Yontem | Yol | Aciklama |
|---|---|---|
| GET | `/enrollment/status` | Enrollment durumunu getir |
| PUT | `/enrollment/status/step/{step}` | Mevcut adimi guncelle (1-6) |
| POST | `/enrollment/status/step/{step}/complete` | Adimi tamamla |
| POST | `/enrollment/status/reset-steps` | Adimlari sifirla |
| POST | `/enrollment/status/complete` | Enrollment'i tamamla |
| POST | `/enrollment/status/refresh` | Sertifika bilgilerini yenile |
| POST | `/enrollment/hot-reload` | Kimlik bilgilerini yeniden yukle |
| GET | `/enrollment/logs` | Denetim loglarini getir |

### Sertifika & Token Yonetimi

| Yontem | Yol | Aciklama |
|---|---|---|
| GET | `/cert/generate-identifier-plist` | CSR plist olustur |
| POST | `/cert/upload-apple-cert` | APNs push sertifikasi yukle |
| POST | `/cert/upload-server-token` | DEP Server Token yukle (.p7m) |
| POST | `/cert/upload-vpp-token` | VPP Token yukle (.vppToken) |
| GET | `/cert/download-dep-cert` | DEP sertifikasi indir (PEM) |
| POST | `/cert/renew-vendor-cert` | Vendor sertifikasi yenile (gizli) |

### SCEP

| Yontem | Yol | Aciklama |
|---|---|---|
| GET | `/scep?operation=GetCACaps` | SCEP CA yetkinliklerini getir |
| GET | `/scep?operation=GetCACert` | SCEP CA sertifikasini getir |
| POST | `/scep?operation=PKIOperation` | SCEP sertifika imzalama |

### Apple Business Manager (DEP + VPP)

| Yontem | Yol | Aciklama |
|---|---|---|
| GET | `/abm/devices` | ABM cihazlarini cek ve senkronize et |
| GET | `/abm/devices/summary` | ABM cihaz ozeti (sayilar + son eklenenler) |
| POST | `/abm/devices/disown` | Cihazlari birak |
| POST | `/abm/profiles` | ABM enrollment profili olustur |
| GET | `/abm/profiles` | Tum profilleri listele |
| GET | `/abm/profiles/{uuid}` | Profil detayi getir |
| DELETE | `/abm/profiles/{uuid}` | Profili sil |
| DELETE | `/abm/profiles/{uuid}/devices` | Profilden cihazlari kaldir |
| POST | `/abm/profiles/{uuid}/assign` | Profili cihazlara ata |
| GET | `/abm/vpp/assets` | VPP varliklarini cek |
| POST | `/abm/vpp/assets/assign` | VPP varliklarini cihazlara ata |
| POST | `/abm/vpp/assets/disassociate` | VPP varliklarini kaldirt |
| GET | `/abm/vpp/assets/assignments` | VPP atamalarini getir |
| POST | `/abm/vpp/assets/revoke` | Cihazdaki tum VPP varliklarini iptal et |
| GET | `/abm/vpp/users` | VPP kullanicilarini getir |

### Cihaz Yonetimi & Komutlar

| Yontem | Yol | Aciklama |
|---|---|---|
| GET | `/devices/{id}/detail` | Cihaz detayi getir (ID ile) |
| GET | `/devices/udid/{udid}/detail` | Cihaz detayi getir (UDID ile) |
| POST | `/devices/{udid}/commands/device-information` | Cihaz bilgisi sorgula |
| POST | `/devices/{udid}/commands/install-app` | Uygulama yukle (VPP: `trackId` veya Enterprise: `identifier` parametresi) |
| POST | `/devices/{udid}/commands/remove-app` | Uygulama kaldir |
| POST | `/devices/{udid}/commands/remove-profile` | Profil kaldir |
| POST | `/devices/{udid}/commands/lock` | Cihazi kilitle |
| POST | `/devices/{udid}/commands/erase` | Cihazi sil (fabrika ayarlari) |
| POST | `/devices/{udid}/commands/restart-device` | Cihazi yeniden baslat |
| POST | `/devices/{udid}/commands/shutdown` | Cihazi kapat |
| POST | `/devices/{udid}/commands/clear-passcode` | Parola temizle |
| POST | `/devices/{udid}/commands/clear-restrictions-password` | Kisitlama sifresi temizle |
| POST | `/devices/{udid}/commands/enable-lost-mode` | Kayip Modu etkinlestir |
| POST | `/devices/{udid}/commands/disable-lost-mode` | Kayip Modu devre disi birak |
| POST | `/devices/{udid}/commands/play-lost-mode-sound` | Kayip Modu sesi cal |
| POST | `/devices/{udid}/commands/location` | Cihaz konumu iste |
| POST | `/devices/{udid}/commands/rename` | Cihazi yeniden adlandir |
| POST | `/devices/{udid}/commands/sync-apps` | Uygulama envanteri senkronize et |
| POST | `/devices/{udid}/commands/device-configured` | Cihaz yapilandirildi bildir |
| POST | `/devices/{udid}/commands/security-info` | Guvenlik bilgisi sorgula |
| POST | `/devices/{udid}/commands/certificates` | Sertifika listesi iste |
| POST | `/devices/{udid}/commands/bluetooth` | Bluetooth ac/kapat |
| POST | `/devices/{udid}/commands/data-roaming` | Veri dolasinimi ac/kapat |
| POST | `/devices/{udid}/commands/personal-hotspot` | Kisisel erisim noktasi ac/kapat |
| POST | `/devices/{udid}/commands/voice-roaming` | Ses dolasimini ac/kapat |
| POST | `/devices/{udid}/commands/diagnostic-submission` | Tani gonderimi ac/kapat |
| POST | `/devices/{udid}/commands/app-analytics` | Uygulama analitigi ac/kapat |
| POST | `/devices/{udid}/commands/wallpaper` | Duvar kagidi ayarla |
| POST | `/devices/{udid}/commands/time-zone` | Saat dilimi ayarla |
| POST | `/devices/{udid}/commands/hostname` | Hostname ayarla |

### Uygulama Yonetimi

| Yontem | Yol | Aciklama |
|---|---|---|
| GET | `/apps/{id}` | iTunes uygulama detayi |
| GET | `/apps/track/{trackId}` | Track ID ile uygulama getir |
| GET | `/apps/bundle/{bundleId}` | Bundle ID ile uygulama getir |
| POST | `/apps/list` | Dinamik filtreleme ile uygulama listele |
| GET | `/apps/genres` | Benzersiz turleri getir |
| POST | `/apps/resolve` | Toplu uygulama metadata cozumle |
| POST | `/enterprise-apps/upload` | Enterprise uygulama yukle (.ipa/.pkg) |
| GET | `/enterprise-apps/{id}` | Enterprise uygulama detayi |
| GET | `/enterprise-apps/bundle/{bundleId}` | Bundle ID ile getir |
| POST | `/enterprise-apps/list` | Dinamik filtreleme ile listele |
| GET | `/enterprise-apps/{id}/download` | Uygulama dosyasini indir |
| GET | `/enterprise-apps/{id}/manifest` | Apple manifest plist getir |
| DELETE | `/enterprise-apps/{id}` | Enterprise uygulamayi sil |
| POST | `/app-groups` | Uygulama grubu olustur |
| GET | `/app-groups` | Gruplari listele |
| GET | `/app-groups/{id}` | Grup detayi |
| POST | `/app-groups/list` | Dinamik filtreleme ile listele |
| PUT | `/app-groups/{id}` | Grubu guncelle |
| DELETE | `/app-groups/{id}` | Grubu sil |
| POST | `/app-groups/{id}/items` | Gruba oge ekle |
| PUT | `/app-groups/{id}/items` | Grup ogelerini degistir |
| DELETE | `/app-groups/{id}/items/{index}` | Ogeyi indekse gore kaldir |

### Hesap Yonetimi

| Yontem | Yol | Aciklama |
|---|---|---|
| POST | `/account` | Hesap olustur |
| GET | `/account` | Tum hesaplari listele |
| GET | `/account/{id}` | Hesap detayi |
| POST | `/account/list` | Dinamik filtreleme ile listele |
| PUT | `/account/{id}` | Hesap guncelle |
| DELETE | `/account/{id}` | Hesap sil (soft delete) |
| POST | `/account/{accountId}/assign/{deviceId}` | Hesabi cihaza ata |
| DELETE | `/account/{accountId}/assign/{deviceId}` | Hesap atamasini kaldirt |

### Agent (iOS/macOS Istemci)

| Yontem | Yol | Aciklama |
|---|---|---|
| POST | `/agent/auth` | Agent kimlik dogrulama |
| GET | `/agent/auth/validate` | Agent oturum token'i dogrula |
| POST | `/api/agent/devices/{udid}/policy` | Cihaza politika gonder (MQTT) |
| POST | `/api/agent/devices/{udid}/request-telemetry` | Anlik telemetri iste |
| POST | `/api/agent/devices/{udid}/request-location` | Anlik konum iste |
| GET | `/api/agent/devices/{udid}/telemetry/latest` | Son telemetriyi getir |
| GET | `/api/agent/devices/{udid}/telemetry` | Telemetri gecmisi |
| GET | `/api/agent/devices/{udid}/location/latest` | Son konumu getir |
| GET | `/api/agent/devices/{udid}/location` | Konum gecmisi |

### Dashboard

| Yontem | Yol | Aciklama |
|---|---|---|
| GET | `/dashboard/stats` | Dashboard istatistikleri (legacy) |
| GET | `/dashboard/device-stats` | Cihaz istatistikleri |
| GET | `/dashboard/command-stats` | Komut istatistikleri |
| GET | `/dashboard/top-apps` | En cok yuklenen uygulamalar |
| GET | `/dashboard/os-versions` | OS versiyon dagilimi |
| GET | `/dashboard/model-distribution` | Cihaz model dagilimi |
| GET | `/dashboard/fleet-telemetry` | Filo telemetri verileri |
| GET | `/dashboard/online-status` | Online/offline cihaz durumu |
| GET | `/dashboard/enrollment-breakdown` | Enrollment tipi dagilimi |
| GET | `/dashboard/security-posture` | Guvenlik durusu |
| GET | `/dashboard/recent-commands` | Son komutlar |
| GET | `/dashboard/device-locations` | Cihaz konumlari |
| GET | `/dashboard/command-trend` | 7 gunluk komut trendi |
| GET | `/dashboard/command-analytics` | Komut tipi dagilimi + basari oranlari |
| GET | `/dashboard/telemetry-analytics` | Pil, WiFi, operator, dil, timezone dagilimi |
| GET | `/dashboard/storage-tiers` | Depolama kapasitesi kademe dagilimi |
| GET | `/dashboard/device-features` | Cihaz ozellik etkinlestirme sayilari |
| GET | `/dashboard/enrollment-trend` | 30 gunluk enrollment trendi |

---

## MDM Protokolu

### Device Enrollment (DEP / Automated Device Enrollment)

```
1. Cihaz fabrikadan ciktiginda veya sifirlanadiginda Apple sunucularina baglanir
2. Apple sunuculari MDM server URL'sini dondurur (ABM'de tanimli)
3. Cihaz, MDM sunucusundan enrollment profilini indirir
   GET /mdm/enrollment
4. Cihaz, Authenticate checkin mesajini gonderir
   POST /mdm/checkin (MessageType: Authenticate)
   -> Cihaz UDID, BuildVersion, OSVersion, ProductName, SerialNumber alinir
   -> AppleDevice entity'si olusturulur veya guncellenir
   -> EnrollmentType.DEP olarak isaretlenir
5. Cihaz, TokenUpdate checkin mesajini gonderir
   POST /mdm/checkin (MessageType: TokenUpdate)
   -> APNs Token ve PushMagic alitir ve kaydedilir
   -> UnlockToken alitir (varsa)
   -> DeviceEnrolledEvent yayinlanir (RabbitMQ)
   -> DeviceInformation ve InstalledApplicationList komutlari kuyruge eklenir
6. Cihaz, komut polling dongusu baslatir
   PUT /mdm/connect
   -> Status: Idle -> Kuyruktan sonraki komutu gonder
   -> Status: Acknowledged -> Onceki komutu isle, sonrakini gonder
   -> Status: Error -> Hatayi isle, sonrakini gonder
   -> Status: NotNow -> Komutu yeniden kuyruge al
   -> Kuyruk bos -> 200 OK (bos yanit)
7. Cihaz kaydi kaldirildiginda
   POST /mdm/checkin (MessageType: CheckOut)
   -> Cihaz durumu DELETED olarak isaretlenir
   -> DeviceDisenrolledEvent yayinlanir
   -> EnrollmentHistory kaydedilir
```

### BYOD / Account-Driven User Enrollment

```
1. Kullanici Ayarlar > Genel > VPN & Cihaz Yonetimi > Is veya Okul Hesabi ile Oturum Ac
2. Managed Apple ID girer (orn: user@company.com)
3. Cihaz, domain'den kesfetme sorgusu yapar:
   GET https://company.com/.well-known/com.apple.remotemanagement?user-identifier=user@company.com
   -> Gateway/proxy bu istegi /api/apple/.well-known/com.apple.remotemanagement'a yonlendirir
   -> Sunucu BYOD ve ADDE BaseURL'lerini dondurur
4. Cihaz, kimlik dogrulama akisini baslatir:

   Simple Auth (varsayilan):
   a) POST /mdm/account-enrollment/byod (Authorization header yok)
      -> 401 Unauthorized + WWW-Authenticate: Basic realm="ArcOps MDM"
   b) Kullanici kimlik bilgilerini girer
   c) POST /mdm/account-enrollment/byod (Authorization: Basic base64(username:password))
      -> AppleIdentity tablosunda kullanici dogrulanir
      -> BCrypt ile sifre kontrolu
      -> Basarili: User Enrollment profili dondurulur (200 + mobileconfig)

   OAuth2 Auth (OAuth2 saglayici tanimli ise):
   a) POST /mdm/account-enrollment/byod?domain=company.com
      -> 401 + OAuth2 auth URL'si dondurulur
   b) Cihaz OAuth2 akisi baslatir (ASWebAuthenticationSession)
   c) Kullanici IdP uzerinden kimlik dogrular
   d) access-token ile enrollment profile istenir

5. Cihaz User Enrollment profilini yukler
   -> EnrollmentID kullanilir (UDID degil, gizlilik icin)
   -> Sinirli MDM yetkileri (kisisel veri ayrilmis)
   -> EnrollmentType.USER_ENROLLMENT olarak isaretlenir

6. Normal MDM akisi devam eder (Authenticate -> TokenUpdate -> Komut polling)
   Not: User Enrollment cihazlarda komut polling'de UDID yerine EnrollmentID kullanilir
```

### Web Tabanli Enrollment Auth (DEP + BYOD)

```
DEP Modu:
- ABM profilinde configurationWebUrl tanimlidir
- Setup Assistant icinde WebView acilir
- GET /mdm/enrollment/web-auth -> Login HTML sayfasi
- POST /mdm/enrollment/web-auth -> Kimlik dogrulama
- Basarili: enrollment profili dogrudan dondurulur (mobileconfig)

BYOD Modu:
- ASWebAuthenticationSession acilir
- GET /mdm/enrollment/web-auth?user-identifier=user@company.com
- POST /mdm/enrollment/web-auth -> Kimlik dogrulama
- Basarili: apple-remotemanagement-user-login://authentication-results?access-token=xxx seklinde redirect

OTP Destegi (IdP senkronize kullanicilar icin):
- POST /mdm/enrollment/web-auth/otp/send?email=user@company.com
- POST /mdm/enrollment/web-auth/otp/verify (email + otp kodu)
```

---

## Command Queue Sistemi

### Redis Implementasyonu (`RedisAppleCommandQueueServiceImpl`)

Uretim ortami icin tasarlanmistir. `apple.command.queue.type=redis` property'si ile aktive edilir.

#### Redis Key Yapisi

| Key Pattern | Tip | Aciklama |
|---|---|---|
| `apple:command:queue:{udid}` | List (FIFO) | Cihaz komut kuyrugu |
| `apple:command:inflight:{udid}` | String | Islenmekte olan komut UUID'si (TTL: 5dk) |
| `apple:command:lock:{udid}` | String | Cihaz bazli dagitik kilit (TTL: 10sn) |
| `apple:command:reaper:lock` | String | Reaper dagitik kilidi (TTL: 25sn) |

#### Akis

**pushCommand (komut kuyruge ekleme):**
1. Komut NSDictionary'den XML'e donusturulur
2. AppleCommand entity'si DB'ye PENDING olarak kaydedilir
3. RedisQueueItem (uuid + commandXml) olarak kuyruga RPUSH yapilir
4. Kuyruk bos ve inflight yoksa APNs wake-up push gonderilir

**popCommand (cihaza komut gonderme):**
1. `apple:command:lock:{udid}` ile dagitik kilit alinir (yarış durumu onleme)
2. `apple:command:inflight:{udid}` kontrol edilir - zaten islem varsa null doner
3. Kuyruktan LINDEX 0 ile ilk eleman okunur (LPOP yapilmaz!)
4. DB'de komut EXECUTING olarak isaretlenir
5. Inflight key'e komut UUID'si yazilir (5dk TTL)
6. NSDictionary parse edilir ve cihaza XML olarak dondurulur
7. Kilit serbest birakilir (Lua script ile)

**handleDeviceResponse (basarili yanit):**
1. CommandUUID ile DB'den komut bulunur
2. Idempotency kontrolu (zaten COMPLETED/FAILED ise atla)
3. Komut turune gore ozel islem:
   - DeviceInformation -> cihaz bilgisi guncelle
   - InstallProfile -> profil kurulum islemi
   - InstalledApplicationList -> yuklu uygulama listesi guncelle
   - ManagedApplicationList -> yonetilen uygulama listesi guncelle
   - InstallApp/RemoveApp -> uygulama envanteri senkronize et
   - SecurityInfo -> guvenlik bilgisi guncelle
   - DeviceLocation -> cihaz konumu kaydet
   - CertificateList -> sertifika listesi guncelle
4. DB'de komut COMPLETED olarak isaretlenir
5. Kuyruktan komut cikarilir (Lua script ile atomik)
6. Inflight temizlenir
7. Kuyrukta baska komut varsa APNs wake-up gonderilir

**handleDeviceErrorResponse (hata yaniti):**
1. Komut bulunur ve idempotency kontrolu yapilir
2. ErrorChain'den hata nedenleri cikarilir
3. Genel komut hatasi islenir (compliance tracking)
4. InstallProfile hatasi -> ozel islem
5. Uygulama kurulum hatasi -> appliedPolicy.applicationManagement.status = FAILED
6. DB'de komut FAILED olarak isaretlenir
7. PolicyApplicationFailedEvent RabbitMQ'ya yayinlanir
8. Temizlik yapilir (kuyruk + inflight)

**handleDeviceNotNowResponse (NotNow yaniti):**
1. Komut FAILED olarak isaretlenir (failureReason: "NotNow")
2. Temizlik yapilir (kuyruk + inflight)

#### Startup Recovery
Uygulama basladiginda:
1. DB'den tum PENDING ve EXECUTING komutlar yuklenir
2. EXECUTING komutlar PENDING'e sifirlanir
3. Tum komutlar Redis kuyruguna eklenir
4. Bekleyen komutlari olan cihazlara APNs wake-up gonderilir

#### Stale Command Reaper
Her 30 saniyede calisan zamanlanmis gorev:
1. Dagitik kilit ile tek instance'da calisir
2. EXECUTING durumunda 5 dakikadan fazla kalan komutlari bulur
3. Bu komutlari FAILED olarak isaretler ("Command timed out")
4. Redis'teki kuyruk ve inflight durumlarini temizler
5. Etkilenen cihazlara APNs wake-up gonderir

### In-Memory Implementasyonu (`InMemoryAppleCommandQueueServiceImpl`)

Gelistirme ortami icin ConcurrentHashMap tabanli implementasyon. Redis bagimliligi gerektirmez.

---

## Policy Uygulama

### ApplePolicyApplicationService

Bir politikanin bir cihaza uygulanma akisi:

```
1. Bos politika kontrolu
   -> Bos ise: mevcut profili kaldir, appliedPolicy temizle, declarative management temizle
   -> Ardindan sendAgentDisableConfig(udid) cagirilir: MQTT uzerinden update_config komutu
      gonderilerek agent'in telemetri ve konum veri toplamasini durdurur
      ({telemetry: false, location: false})

2. Politika derin kopyasi olusturulur (orijinal degistirilmez)

3. Cihaz platformu tespit edilir (ProductName'den):
   - iphone/ipod -> IOS
   - ipad -> IPADOS
   - mac -> MACOS
   - apple tv/appletv -> TVOS
   - vision/reality -> VISIONOS
   - watch -> WATCHOS

4. Uygun PlatformPayloadStrategy secilir (Strategy Pattern)

5. Platform uyumluluk kontrolu
   -> Politika platformu ile cihaz platformu eslesmiyor ise atla

6. PolicyContext olusturulur (platform + managementChannel)
   - USER_CHANNEL: User Enrollment cihazlari (sinirli yetkiler)
   - SUPERVISED: DEP cihazlari (tam yetki)
   - UNSUPERVISED: Profil tabanli cihazlar

7. Payload NSDictionary'ler olusturulur (strategy.buildPayloads)

8. Zorunlu uygulamalar kurulur (executeRequiredAppsInstall):
   a. payload.applicationsManagement.requiredApps okunur
   b. Dogrudan belirtilen uygulamalar listelenir
   c. App Group referanslari cozumlenir:
      - Grup ID'leri ile AppGroup entity'leri yuklenir
      - Her grup ogesinin platformu kontrol edilir (iTunes metadata'dan)
      - VPP uygulamalari: trackId veya bundleId ile install komutu
      - Enterprise uygulamalari: bundleId ile install komutu
   d. Her uygulama icin installApp komutu kuyruge eklenir

9. Declarative Management islenir (executeDeclarativeManagement):
   - Mevcut ve istenen declaration token karsilastirilir
   - Degisiklik varsa DeclarativeManagement komutu gonderilir
   - accounts (CalDAV, CardDAV, Google, LDAP, Exchange, Mail)
   - softwareUpdate, watchEnrollment ayarlari

10. Settings komutu gonderilir (executeSettingsCommand):
    - DeviceName sablonu cozumlenir ({serial_number}, {device_name}, vb.)
    - Settings payload'lari olusturulur ve gonderilir

11. InstallProfile komutu ile yapilandirma profili gonderilir:
    - NSDictionary'ler Profile XML'ine donusturulur
    - Base64 kodlanir
    - InstallProfile komutu kuyruge eklenir

12. Agent veri toplama yapilandirmasi gonderilir (MQTT update_config):
    - extractAgentCapabilities() ile politikadaki dataServices bolumu cikarilir
    - dataServices bolumu varsa: telemetri/konum ayarlari (enableTelemetry, enableLocationTracking,
      telemetryIntervalSeconds, locationIntervalSeconds) MQTT ile agent'a gonderilir
    - dataServices bolumu YOKSA: sendAgentDisableConfig(udid) cagirilir, agent'a
      {telemetry: false, location: false} gonderilerek veri toplama durdurulur
    - Bu islem idempotent'tir: agent halihazirda veri toplamiyorsa bile guvenle gonderilebilir

13. appliedPolicy device entity'sine kaydedilir
```

### PlatformPayloadStrategy (Strategy Pattern)

Her platform icin farkli payload olusturma stratejisi:

| Strateji | Platform | Aciklama |
|---|---|---|
| `IosPolicyPayloadStrategy` | IOS | iPhone/iPod Touch kisitlama ve yapilandirmalari |
| `IpadosPolicyPayloadStrategy` | IPADOS | iPad'e ozel ek ozellikler (iOS stratejisinden turetilir) |
| `MacosPolicyPayloadStrategy` | MACOS | macOS'e ozel yapilandirmalar, kiosk lockdown destegi (10.14+), DirectoryService (AD) |
| `TvosPolicyPayloadStrategy` | TVOS | Apple TV kisitlamalari |
| `VisionosPolicyPayloadStrategy` | VISIONOS | Apple Vision Pro kisitlamalari |
| `WatchosPolicyPayloadStrategy` | WATCHOS | Apple Watch kisitlamalari |

### Declarative Management (DDM) Enrichment

Policy uygulanmadan once, `ApplePolicyApplyListener` payload'dan otomatik olarak DDM yapilandirmalari uretir:

- **Hesaplar**: CalDAV, CardDAV, Google, LDAP, Exchange, Mail hesaplari
  - Her hesap turu icin Apple DDM configuration tipi eslenir
  - Kimlik bilgileri (credential) asset referanslari olarak baglanir
  - Identity asset'ler inline, credential asset'ler DataURL referansi olarak eklenir
- **Guvenlik Yapilandirmasi**: softwareUpdate, watchEnrollment
- **Degisiklik Tespiti**: SHA-256 hash ile icerik degisikligini tespit eder, degisiklik yoksa komut gondermez

---

## APNs Entegrasyonu

MDM cihazlarini uyandirmak icin Apple Push Notification service kullanilir.

| Ozellik | Deger |
|---|---|
| Kutuphane | Pushy 0.15.4 |
| Sunucu | Apple Production APNs |
| Sertifika | P12 dosyasi (`certs/apple/mdm_customer_push.p12`) |
| Push Turu | MDM (PushType.MDM) |
| Oncelik | IMMEDIATE (DeliveryPriority.IMMEDIATE) |
| Payload | `{"mdm": "<pushMagic>"}` |
| Max Deneme | 5 |
| Backoff | Ustel (250ms baz, %10 jitter, max 5s) |
| Yenilenebilir Rejectionlar | TooManyRequests, ServiceUnavailable, InternalServerError, Shutdown, IdleTimeout |
| Hot Reload | Sertifika yuklendiginde APNs client yeniden baslatilir |

---

## MQTT Entegrasyonu (Agent Iletisimi)

iOS/macOS agent uygulamasi ile sunucu arasindaki cift yonlu iletisim.

### Yapilandirma

| Ozellik | Deger |
|---|---|
| Broker URL | `tcp://localhost:1883` |
| Client ID | `arcops-apple-mdm-server` |
| QoS | 1 (en az bir kez teslim) |
| Clean Session | true |
| Auto Reconnect | true |
| Keep Alive | 30 saniye |

### Topic Yapisi

**Sunucu Dinliyor (Subscribe):**

| Topic Pattern | Aciklama |
|---|---|
| `arcops/devices/+/status` | Cihaz durum degisiklikleri (online/offline) |
| `arcops/devices/+/telemetry` | Periyodik telemetri verileri |
| `arcops/devices/+/location` | Periyodik konum verileri |
| `arcops/devices/+/events` | Cihaz olaylari |
| `arcops/devices/+/responses` | Komut yanitlari |

**Sunucu Yayin Yapiyor (Publish):**

| Topic Pattern | Aciklama |
|---|---|
| `arcops/devices/{udid}/commands` | Cihaza komut gonderme |

### Bilesenler

| Sinif | Aciklama |
|---|---|
| `MqttConfig` | Spring Integration MQTT inbound/outbound adapter yapilandirmasi |
| `MqttProperties` | MQTT baglanti parametreleri |
| `MqttMessageRouter` | Topic'e gore mesajlari ilgili servise yonlendirir |
| `AgentPresenceService` | Cihaz online/offline durumunu yonetir, `AgentPresenceHistory` kaydeder |
| `AgentTelemetryService` | Telemetri mesajlarini isler, `AgentTelemetry` kaydeder |
| `AgentLocationService` | Konum mesajlarini isler, `AgentLocation` kaydeder |
| `AgentCommandService` | Cihazlara MQTT uzerinden komut gonderir, `AgentCommand` tablosunda takip eder |
| `AgentCommand` | MQTT agent komutlarinin DB entity'si (PENDING → SENT → COMPLETED/FAILED) |
| `AgentCommandRepository` | AgentCommand CRUD + commandUuid/deviceIdentifier/status sorgulari |

---

## RabbitMQ Event Sistemi

### Dinleyiciler (back_core'dan gelen event'ler)

| Dinleyici | Kuyruk | Aciklama |
|---|---|---|
| `ApplePolicyApplyListener` | `POLICY_APPLY_QUEUE_APPLE` | Politikayi cihaza uygula |
| `ApplePolicyCreatedListener` | `POLICY_CREATE_QUEUE_APPLE` | Yeni politikayi DB'ye kaydet |
| `ApplePolicyUpdatedListener` | (guncelleme kuyrugu) | Politika guncelleme senkronizasyonu |
| `ApplePolicyDeletedListener` | (silme kuyrugu) | Politika silme senkronizasyonu |
| `IdentitySyncEventListener` | `IDENTITY_SYNC_QUEUE_APPLE` | Kimlik olustur/guncelle (UPSERT) |
| `IdentitySyncEventListener` | `IDENTITY_DELETED_QUEUE_APPLE` | Kimlik soft-delete |
| `AccountSyncEventListener` | `ACCOUNT_CORE_SYNC_QUEUE_APPLE` | Hesap olustur/guncelle — back_core'dan ayni UUID ile (UPSERT) |

### Yayincilar (back_core'a giden event'ler)

| Yayinci | Event | Aciklama |
|---|---|---|
| `DeviceEventPublisher` | `DeviceEnrolledEvent` | Cihaz basariyla kayit oldu |
| `DeviceEventPublisher` | `DeviceDisenrolledEvent` | Cihaz kaydi kaldirildi |
| `DeviceEventPublisher` | `DevicePresenceChangedEvent` | Cihaz online/offline durum degisimi (AgentPresenceService tarafindan tetiklenir) |
| `PolicyEventPublisher` | `PolicyApplicationFailedEvent` | Politika uygulama basarisiz |
| `AccountEventPublisher` | `AccountCreatedEvent` | Hesap olusturuldu |

### RabbitMQ Yapilandirmasi

| Ozellik | Deger |
|---|---|
| Host | localhost:5673 |
| Kullanici / Sifre | admin / admin |
| Retry | Etkin (max 5 deneme, 1s baslangic, 2x carpan, max 10s) |
| Exchange Tipi | Topic |
| Missing Queues Fatal | false |

---

## SCEP Sertifika

Simple Certificate Enrollment Protocol ile cihaz sertifika kaydi.

| Ozellik | Deger |
|---|---|
| CA Sertifikasi | `certs/apple/cacert.crt` |
| CA Anahtari | `certs/apple/cakey.key` |
| Subject | `CN=uconos.com, O=MOLSEC` |
| Ulke | TR |
| Sehir | Istanbul |

### Desteklenen SCEP Islemleri

| Islem | Yontem | Aciklama |
|---|---|---|
| GetCACaps | GET | CA yetkinlikleri (POSTPKIOperation, SHA-256, AES vb.) |
| GetCACert | GET | CA sertifikasini DER formatinda dondur |
| PKIOperation | POST | CSR imzalama ve sertifika olusturma |

---

## Agent Auth

iOS/macOS agent uygulamasi icin kimlik dogrulama. Password ve OTP olmak uzere iki yontem desteklenir.

### Akis — Password

```
1. POST /agent/auth
   Body: { "username": "user@company.com", "password": "xxx", "deviceSerialNumber": "...", "agentVersion": "..." }

2. AppleIdentity tablosunda kullanici aranir (username veya email ile)

3. Kontroller:
   - Kimlik bulunamadi -> 401 Unauthorized
   - Kimlik ACTIVE degil -> 403 Forbidden
   - Sifre hash'i yok -> 401 Unauthorized
   - BCrypt sifre eslesmesi basarisiz -> 401 Unauthorized

4. Basarili ise: buildAuthResponse() ortak metodu cagrilir (authSource: "AGENT")
```

### Akis — OTP (Email Code)

```
1. POST /agent/auth/otp/send
   Body: { "email": "user@company.com" }
   - AppleIdentity email ile aranir, ACTIVE kontrolu
   - 6 haneli OTP uretilir (SecureRandom)
   - Redis: "agent:otp:{email}" -> otp (TTL: 5 dk)
   - HTML email gonderilir (JavaMailSender)
   - Kimlik bulunamadi veya inactive -> 404

2. POST /agent/auth/otp/verify
   Body: { "email": "user@company.com", "otp": "123456", "deviceSerialNumber": "...", "agentVersion": "..." }
   - Redis'ten OTP dogrulanir ve silinir
   - Gecersiz/suresi dolmus -> 401
   - Basarili ise: buildAuthResponse() ortak metodu cagrilir (authSource: "AGENT_OTP")
```

### Ortak Auth Response (buildAuthResponse)

```
- UUID session token olusturulur -> Redis "agent:session:{token}" (TTL: 24 saat)
- Device serial number ile AppleDevice resolve edilir
- Device-Account binding yapilir
- DeviceAuthHistory kaydedilir
- AgentAuthResponse dondurulur: { token, deviceUdid, user, mqtt }
```

### Token Dogrulama / Logout

```
GET /agent/auth/validate — Header: Authorization: Bearer {token}
POST /agent/auth/logout  — Header: Authorization: Bearer {token}
```

## Agent Activity Log

Agent islemleri (screen share, terminal, notification) icin kalici loglama.

### Tablo: agent_activity_log (V17)

| Kolon | Tip | Aciklama |
|-------|-----|----------|
| id | UUID PK | gen_random_uuid() |
| device_id | UUID FK -> apple_device | nullable |
| device_identifier | VARCHAR(255) | UDID |
| activity_type | VARCHAR(30) | SCREEN_SHARE / REMOTE_TERMINAL / NOTIFICATION |
| status | VARCHAR(20) | STARTED / COMPLETED / FAILED |
| details | JSONB | captureType, title, body, deliveryChannel vb. |
| session_id | VARCHAR(255) | screen share/terminal session ID |
| started_at | TIMESTAMP | |
| ended_at | TIMESTAMP | nullable |
| duration_seconds | BIGINT | nullable |
| initiated_by | VARCHAR(255) | "admin" |
| created_at | TIMESTAMP | |

### Entegrasyonlar

- **ScreenShareController**: start -> logStart("SCREEN_SHARE", sessionId, {captureType}), stop -> logComplete(sessionId)
- **RemoteTerminalController**: start -> logStart("REMOTE_TERMINAL", sessionId, {}), stop -> logComplete(sessionId)
- **AgentNotificationController**: send -> logNotification({title, body, category}, channel)
- **AppleDeviceDetailServiceImpl**: Son 50 activity log Device Detail DTO'ya eklenir (agentActivityLog field)

---

## Flyway Migrations

| Versiyon | Dosya | Aciklama |
|---|---|---|
| V1 | `V1__Initial_Script.sql` | Cekirdek MDM tablolari (19 tablo): apple_device, apple_account, apple_command, apple_device_information, apple_device_apps, apple_device_location, policy, enrollment_status, enrollment_audit_log, abm_device, abm_profile, enterprise_app, itunes_app_meta, app_group, app_group_item, app_supported_platforms, enterprise_app_supported_platforms, apple_account_devices (ara tablo) |
| V2 | `V2__Add_Identity.sql` | apple_identity tablosu |
| V3 | `V3__.sql` | (Kucuk duzeltme/bos migrasyon) |
| V4 | `V4__Add_Abm_Device_Status.sql` | AbmDevice'a abmStatus alani ekleme |
| V5 | `V5__Add_Enrollment_History.sql` | enrollment_history tablosu |
| V6 | `V6__Add_Agent_Presence.sql` | agent_presence_history tablosu + apple_device'a agent alanlari |
| V7 | `V7__Add_Agent_Telemetry_Location.sql` | agent_telemetry ve agent_location tablolari + indeksler |
| V8 | `V8__Presence_History_Event_Type.sql` | agent_presence_history tablosuna event_type kolonu eklenmesi (oturum modelinden olay logu modeline gecis) |
| V9 | `V9__Add_Wifi_SSID_To_Telemetry.sql` | agent_telemetry tablosuna wifi_ssid kolonu eklenmesi |
| V10 | `V10__Add_Agent_Push_Token.sql` | apple_device tablosuna agent_push_token kolonu eklenmesi (APNs agent wake-up icin) |
| V11 | `V11__Add_Location_Source.sql` | agent_location tablosuna source kolonu eklenmesi (VARCHAR(20), default 'AGENT'). Degerler: AGENT, MDM_LOST_MODE |
| V12 | `V12__Add_Device_Auth_History.sql` | device_auth_history tablosu eklenmesi (agent login/logout gecmisi takibi) |
| V13 | `V13__Add_App_Catalog_Assignment.sql` | app_catalog_assignment tablosu eklenmesi (AppGroup → Account/AccountGroup atama iliskisi) |
| V14 | `V14__Add_System_Setting.sql` | system_setting tablosu eklenmesi (id + operation_identifier + JSONB value + audit timestamps) |
| V15 | `V15__Add_App_Report_Indexes.sql` | apple_device_apps tablosuna GIN trigram indeksleri eklenmesi (bundle_identifier, name, version kolonlari) — bulanik uygulama arama performansi icin |
| V16 | `V16__Add_User_List_To_Device_Information.sql` | apple_device_information tablosuna user_list (JSONB) kolonu eklenmesi — UserList MDM komutu yanit verilerinin saklanmasi icin |
| V17 | `V17__Add_Agent_Activity_Log.sql` | agent_activity_log tablosu eklenmesi (screen share, terminal, notification islem logları). JSONB details, session_id, duration_seconds alanlari + indeksler |

---

## Guvenlik Yapilandirmasi

Iki katmanli Spring Security yapilandirmasi:

**Filter Chain 1 (Order 1) - MDM Yollari:**
- `.well-known/**`, `/mdm/**`, `/agent/**` yollari JWT dogrulamasi ATLANIR
- Apple cihazlari UUID tabanli enrollment token'lar gonderir (JWT degil)
- Tum istekler izin verilir, CSRF/CORS devre disi

**Filter Chain 2 (Order 2) - Varsayilan:**
- Diger tum yollar
- OAuth2 Resource Server (JWT) yapilandirmasi
- JWK Set URI: `http://localhost:8080/oauth2/jwks`
- Tum istekler izin verilir (permitAll), JWT sadece kimlik bilgisi icin kullanilir

**Sifre Kodlama:**
- BCryptPasswordEncoder (agent auth icin)

---

## Yapilanlar

- [x] MDM checkin akisi (Authenticate, TokenUpdate, CheckOut)
- [x] MDM connect komut polling
- [x] Declarative Management destegi (hesaplar, softwareUpdate, watchEnrollment)
- [x] DEP enrollment (profil indirme, ABM profil olusturma, cihaz atama)
- [x] BYOD / Account-Driven User Enrollment (Simple + OAuth2 auth)
- [x] ADDE (Account-Driven Device Enrollment)
- [x] Web tabanli enrollment auth (DEP WebView + BYOD ASWebAuthenticationSession)
- [x] OTP tabanli enrollment (IdP senkronize kullanicilar icin)
- [x] .well-known/com.apple.remotemanagement service discovery
- [x] Redis tabanli komut kuyrugu (dagitik kilit, Lua script, inflight takibi)
- [x] In-memory komut kuyrugu (gelistirme ortami)
- [x] Stale command reaper (30sn periyot, 5dk TTL)
- [x] Startup recovery (DB'den kuyruk yukleme)
- [x] APNs push notification (Pushy, ustel backoff, 5 deneme)
- [x] APNs credential hot-reload
- [x] Policy uygulama motoru (platform bazli strateji, derin kopyalama)
- [x] Platform bazli payload stratejileri (iOS, iPadOS, macOS, tvOS, visionOS, watchOS)
- [x] Zorunlu uygulama kurulumu (VPP + Enterprise, app group cozumleme, platform filtreleme)
- [x] Uygulama kaldirma (politika degisikliginde removeOnRemoval)
- [x] SCEP sertifika servisi
- [x] Apple Business Manager entegrasyonu (DEP + VPP API)
- [x] VPP uygulama dagitimi (lisans atama, kaldirma, iptal)
- [x] Enterprise uygulama yukleme (.ipa/.pkg parse, metadata cikarma, manifest olusturma)
- [x] Uygulama gruplari (VPP + Enterprise, platform filtreleme)
- [x] Dinamik filtreleme ile listeleme (DynamicListRequestDto)
- [x] Cihaz komutlari (kilit, sil, yeniden baslat, kapat, kayip modu, uygulama yonetimi, ayarlar vb.)
- [x] Cihaz bilgi sorgulama (DeviceInformation, SecurityInfo, CertificateList)
- [x] MQTT agent iletisimi (telemetri, konum, presence, komut)
- [x] Agent kimlik dogrulama (BCrypt + Redis oturum)
- [x] Agent telemetri ve konum kaydi
- [x] Agent presence takibi (online/offline, oturum gecmisi)
- [x] RabbitMQ event sistemi (politika senkronizasyonu, kimlik senkronizasyonu)
- [x] Enrollment wizard (6 adimli ilerleyis, sertifika durum izleme)
- [x] Enrollment denetim loglari
- [x] Enrollment gecmisi (enrollment/unenrollment kaydi)
- [x] Hesap yonetimi (CRUD + cihaz atama)
- [x] Dashboard istatistikleri (temel: topManagedApps, commandStats, deviceStats, osVersions, modelDistribution)
- [x] Dashboard gelismis istatistikler: DashboardStatsDto'ya 9 yeni nested DTO ve 7 yeni alan eklendi (fleetTelemetry, onlineStatus, enrollmentTypeDistribution, securityPosture, recentCommands, deviceLocations, commandTrend). 5 repository'ye yeni sorgular eklendi (AgentTelemetryRepository.findLatestPerDevice, AgentLocationRepository.findLatestPerDevice, AppleDeviceRepository online/offline/neverSeen/enrollmentType sorgulari, AppleDeviceInformationRepository activationLock/cloudBackup count, AppleCommandRepository recentCommands/dailyCounts). DashboardStatsService'e 7 yeni build metodu eklendi
- [x] OS version dagitiminda platform bilgisi: findOsVersionDistribution() sorgusu product_name uzerinden platform cikarimi yapiyor (iPhone/iPad→iOS, Mac→macOS, TV→tvOS, Vision→visionOS). OsVersionCountDto'ya platform alani eklendi
- [x] Enrollment davet e-postasi
- [x] QR kod ile enrollment URL
- [x] Cihaz adi sablonu cozumleme ({serial_number}, {device_name}, {account_name} vb.)
- [x] Policy compliance tracking
- [x] Consul service discovery kaydı
- [x] Soft delete desteği (AppleDevice, AppleAccount, AppleIdentity, Policy)
- [x] DeviceCommandController.installApp endpoint'i genisletildi: eskiden sadece zorunlu `trackId` (Integer) kabul ederken, artik hem `trackId` (Integer, opsiyonel, VPP uygulamalari icin) hem `identifier` (String, opsiyonel, Enterprise uygulamalari icin bundle ID) kabul ediyor. En az birinin saglanmasi zorunlu. AppleCommandSenderService.installApp() zaten her iki modu destekliyordu, bu degisiklik yalnizca controller katmanini genisletti
- [x] Flyway veritabani migrasyonlari (V1-V8)
- [x] Cihaz detay DTO'suna presence gecmisi eklendi (GetAppleDeviceDetailDto.presenceHistory + PresenceSessionDto ic sinifi: connectedAt, disconnectedAt, durationSeconds, disconnectReason, agentVersion, agentPlatform)
- [x] AppleDeviceDetailServiceImpl'e AgentPresenceHistoryRepository bagimliligi eklendi; son 50 presence oturumu getirilerek PresenceSessionDto'ya map'leniyor
- [x] Agent auth cihaz arama yontemi degistirildi: hesap zinciri yerine AppleDeviceRepository.findBySerialNumber() ile seri numarasi uzerinden arama
- [x] AppleDeviceRepository'ye findBySerialNumber() sorgusu eklendi
- [x] AppleCommandBuilderService ve Impl'e NSDictionary configuration parametreli installAppFromManifest overload'u eklendi (managed app config destegi)
- [x] AppleCommandSenderServiceImpl'e buildManagedAppConfig() metodu eklendi: enterprise uygulama kurulumunda serverURL, organizationName, deviceSerialNumber bilgilerini Configuration dict'e set ediyor
- [x] Presence history modeli oturum tabanlindan (session) olay logu modeline (event log) donusturuldu. Her satir artik bir durum gecisini (ONLINE veya OFFLINE) temsil ediyor; 30sn heartbeat'lerden kaynaklanan DB sismesini onlemek icin yalnizca gercek durum degisiklikleri kaydediliyor
- [x] V8__Presence_History_Event_Type.sql Flyway migrasyonu eklendi: agent_presence_history tablosuna `event_type` kolonu eklendi
- [x] AgentPresenceHistory entity'si guncellendi: connectedAt/disconnectedAt/disconnectReason oturum alanlari yerine eventType, timestamp, reason olay alanlari kullaniliyor
- [x] AgentPresenceHistoryRepository sorgulari olay modeli icin guncellendi (findByConnectedAt yerine findByTimestamp)
- [x] AgentPresenceService yeniden yazildi: heartbeat'ler yalnizca agent_last_seen_at'i guncelliyor, INSERT yalnizca gercek durum gecislerinde yapiliyor (offline->online = ONLINE olayi, online->offline = OFFLINE olayi). durationSeconds onceki durumun suresini tutuyor. detectStaleDevices() artik dogrudan handleOffline() cagiriyor. Startup temizligi markOnlineDevicesOfflineOnStartup() ile degistirildi
- [x] AppleDeviceRepository'ye findByAgentOnlineTrue() sorgusu eklendi
- [x] GetAppleDeviceDetailDto.PresenceSessionDto, PresenceEventDto olarak yeniden adlandirildi: eventType, timestamp, durationSeconds, reason, agentVersion, agentPlatform alanlari
- [x] AppleCommandHandlerServiceImpl.handleManagedApplicationList() bug fix: Eskiden yalnizca `Status == "Managed"` olan uygulamalarda `managed=true` set ediliyordu; ancak Apple yeni kurulan uygulamalar icin "Installing" gibi farkli status degerleri donduruyor, bu yuzden MDM ile kurulan uygulamalar UI'da "Managed" olarak gorunmuyordu. Yeni mantik: once tum mevcut uygulamalarda `managed=false` set ediliyor (ManagedApplicationList gercek kaynak oldugu icin), sonra ManagedApplicationList yanitinda bulunan HER uygulama icin Status degerinden bagimsiz olarak `managed=true` set ediliyor
- [x] AppleCommandHandlerServiceImpl'e AgentTelemetryRepository bagimliligi eklendi. DeviceInformationChangedEvent publish edilmeden once buildAgentInfoMap() ile agent durumu (online, last_seen_at, agent_version) ve son telemetri verisi (ip_address, wifi_ssid, battery, storage, jailbreak vb.) event'e agentInfo olarak ekleniyor. 3 publish noktasi da guncellendi (updateDeviceInfo, handleInstallProfileCommand, handleSecurityInfoResponse)
- [x] Policy dataServices bolumu ve MQTT agent config gonderimi: Politikadaki `dataServices` bolumu (enableTelemetry, enableLocationTracking, telemetryIntervalSeconds, locationIntervalSeconds) parse edilerek MQTT uzerinden `update_config` komutu ile iOS/macOS agent'a gonderiliyor. AbstractPlatformPayloadStrategy'ye ortak `extractDataServicesForAgent()` helper eklendi. IosPolicyPayloadStrategy ve MacosPolicyPayloadStrategy'de `extractAgentCapabilities()` override edildi. ApplePolicyApplicationServiceImpl'e AgentCommandService inject edildi ve TODO blogu kaldirildi; agent capabilities cikartilip bos degilse MQTT ile cihaza gonderiliyor
- [x] AgentTelemetry, AgentLocation, AgentPresenceHistory entity'lerindeki `device` alanina `@JsonIgnore` eklendi: REST API yanitlarinda tam AppleDevice grafinin serialize edilmesi onlendi (N+1 sorgu ve buyuk JSON yanit sorunu cozuldu)
- [x] AgentPresenceHistoryRepository, AgentTelemetryRepository, AgentLocationRepository'ye zaman araligina gore sorgulama metotlari eklendi: `findByDeviceIdentifierAndTimestampBetween` (presence), `findByDeviceIdentifierAndDeviceCreatedAtBetween` (telemetry, location) -- frontend'den from/to parametreleri ile gecmis verilerin filtrelenmesini sagliyor
- [x] AgentDeviceController'a `GET /{udid}/presence` endpoint'i eklendi: opsiyonel `from`/`to` query parametreleri ile belirli zaman araligindaki presence gecmisini dondurur; parametresiz cagrildiginda tum gecmisi getirir
- [x] Mevcut telemetri ve konum gecmisi endpoint'lerine (`GET /{udid}/telemetry`, `GET /{udid}/location`) opsiyonel `from`/`to` query parametreleri eklendi: zaman araligi bazli filtreleme destegi
- [x] AgentPresenceService'e `resendAgentConfigIfNeeded()` metodu eklendi: cihaz offline->online gecisi yaptiginda, cleanSession=true MQTT ayari nedeniyle kaybolan mesajlari telafi etmek icin agent config'ini MQTT uzerinden yeniden gonderir
- [x] AppleCommandRepository.findByDeviceIdOrderByRequestTimeDesc() metodu Pageable parametresi alacak sekilde guncellendi: komut gecmisi sorgusu artik sayfalanmis sonuc donduruyor. AppleDeviceDetailServiceImpl bu metodu `PageRequest.of(0, 50)` ile cagirarak cihaz basina en fazla 50 komut kaydini getiriyor (eskiden tum komut gecmisi getiriliyordu)
- [x] TokenUpdate User Channel ayrimi: macOS cihazlar enroll olurken iki TokenUpdate gonderir — Device Channel (token, pushMagic, unlockToken) ve User Channel (UserID, UserLongName, UserShortName). Eskiden ikisi ayni sekilde isleniyordu ve user token device token'in uzerine yaziyordu. Artik tokenUpdate() metodu UserID varligini kontrol ediyor; User Channel ise device token'a dokunmadan bilgileri AppleDeviceInformation.userChannel JSONB alanina kaydediyor. V18 Flyway migrasyonu ile apple_device_information tablosuna user_channel kolonu eklendi
- [x] AgentDataRetentionService eklendi: 7 gundan eski agent verilerini temizleyen zamanlanmis servis (saatte bir calisir). AgentPresenceHistoryRepository, AgentTelemetryRepository ve AgentLocationRepository'deki `deleteByTimestampBefore` / `deleteByDeviceCreatedAtBefore` metotlarini kullanir
- [x] AgentPresenceHistoryRepository, AgentTelemetryRepository, AgentLocationRepository'ye `deleteByTimestampBefore` ve `deleteByDeviceCreatedAtBefore` metotlari eklendi: AgentDataRetentionService tarafindan eski verilerin toplu silinmesi icin kullaniliyor
- [x] AgentLocation, AgentTelemetry, AgentPresenceHistory entity'lerine `@JsonIgnoreProperties` eklendi: REST API yanitlarinda temiz JSON serializasyonu saglamak icin (lazy-loaded iliskilerin serializasyon sorunlari onlendi)
- [x] MqttMessageRouter, AbstractPlatformPayloadStrategy, AgentPresenceService, ApplePolicyApplicationServiceImpl yeniden duzenlendi (refactored): hardcoded string anahtarlar yerine commons modülündeki `AgentDataServiceKeys` sabitleri sinifi kullaniliyor
- [x] ApplePolicyApplicationServiceImpl bug fix: Politika kaldirildiginda (handleEmptyPolicy) veya dataServices bolumu olmayan yeni politika uygulandiginda agent'a MQTT uzerinden `update_config` komutu gonderilmiyordu, bu nedenle agent eski yapilandirmasiyla telemetri/konum verisi toplamaya devam ediyordu. Duzeltme: `sendAgentDisableConfig(String udid)` private metodu eklendi -- AgentCommandService uzerinden `{telemetry: false, location: false}` iceren `update_config` komutu gonderiyor. handleEmptyPolicy() artik appliedPolicy temizlendikten sonra bu metodu cagiriyor. extractAgentCapabilities() bos dondugunde (dataServices bolumu yoksa) sadece log yazmak yerine sendAgentDisableConfig() cagiriliyor. Islem idempotent: agent halihazirda veri toplamiyorsa bile guvenle gonderilebilir
- [x] WebRTC Ekran Paylasimi — Faz 1 Backend Sinyal Altyapisi: WebSocket signaling endpoint (`/screen-share/ws`), Redis oturum yonetimi (`ScreenShareSessionService`), WebSocket↔MQTT sinyal koprusu (`ScreenShareSignalingHandler`), REST controller (`ScreenShareController` — start/stop/status), MqttMessageRouter'da screen-share response routing. Dosyalar: `configs/websocket/WebSocketConfig.java`, `services/screenshare/ScreenShareSession.java`, `services/screenshare/ScreenShareSessionService.java`, `services/screenshare/ScreenShareSignalingHandler.java`, `controllers/ScreenShareController.java`. `spring-boot-starter-websocket` dependency eklendi. `AgentDataServiceKeys`'e `CMD_START_SCREEN_SHARE`, `CMD_STOP_SCREEN_SHARE`, `CMD_WEBRTC_OFFER`, `CMD_WEBRTC_ICE` sabitleri eklendi (commons)
- [x] APNs Agent Push Service — Cevrimdisi iOS agent cihazlari uyandirmak icin silent push notification destegi. `AgentPushService.java`: .p8 Auth Key tabanli APNs istemcisi, sandbox/production `apns.agent.production` property ile yapilandirilabilir. `AgentNotificationController.java`: `POST /notifications/send/{udid}` REST endpoint'i ile agent cihazlara push notification gonderme. `PendingNotificationService.java`: Cihaz cevrimdisiyken bildirimleri Redis kuyrugundan saklama (TTL 5 dk), cihaz baglanti kurdugundu kuyrugu bosaltma
- [x] Offline Screen Share Wake-Up — Cihaz cevrimdisiyken silent push ile uyandirma, ardindan otomatik ekran paylasimi baslatma. `PendingScreenShareService.java`: Redis pending kuyrugu (`screenshare:pending:{udid}`, TTL 60s). `ScreenShareController.java` guncellendi: Online → 200, Offline+pushToken → 202 "waking" (silent push gonderir), Token yok → 400. `ScreenShareSession.java`'ya `STATE_WAKING` sabiti eklendi. `AgentPresenceService.java`'da `flushPendingScreenShare()` metodu: cihaz tekrar baglandiginda oturum gecerliligi kontrol edilerek ekran paylasimi komutu gonderiliyor
- [x] Screen Share Signaling Hardening — `ScreenShareSignalingHandler.java`'da `ConcurrentWebSocketSessionDecorator` ile thread-safe WebSocket gonderimi. `sessions.compute()` ile baglanti kurulumunda atomik buffer-drain (mesaj siralama yarisi onlendi). Stop komutu artik in-memory map'leri ve Redis oturumunu duzgun temizliyor
- [x] Bidirectional Signaling Buffer — `ScreenShareSignalingHandler.java`'da tek yonlu `pendingMessages` yerine cift yonlu `SignalingBuffer` yapisi. Browser→Device (offer/ICE): agent `screen_share_ready` gonderene kadar `toDevice` kuyruğunda bekletilir, ready gelince flush edilir. Device→Browser (answer/ICE/ready): WebSocket baglanana kadar `toBrowser` kuyruğunda bekletilir, WS connect olunca flush edilir. Ilk denemede "connecting webrtc" durumunda kalma sorunu cozuldu
- [x] AppleDevice entity'sine `agentPushToken` kolonu eklendi (V10__Add_Agent_Push_Token.sql Flyway migrasyonu) — iOS agent APNs push token'larinin saklanmasi icin
- [x] V9__Add_Wifi_SSID_To_Telemetry.sql Flyway migrasyonu: agent_telemetry tablosuna wifi_ssid kolonu eklendi
- [x] Flyway veritabani migrasyonlari (11 versiyon)
- [x] Alert Push Notification — Offline cihazlara silent push yerine gorunur alert push gonderimi. `AgentPushService.java`'ya `sendAlertNotification()` metodu eklendi (PushType.ALERT, DeliveryPriority.IMMEDIATE, title/body/sound/customData). `AgentNotificationController.java` guncellendi: offline cihazlara alert push ile bildirim icerigi dogrudan gonderiliyor (iOS uygulama force-quit olsa bile bildirim gorunur). `AgentPresenceService.java`'da flush edilen bildirimlere `pushAlreadySent: true` ekleniyor (iOS'ta duplicate local notification onleniyor). Redis kuyrugu korunuyor (in-app bildirim listesi icin)
- [x] Screen Share Alert Push — Offline cihazlara ekran paylasimi talebi icin alert push gonderimi. `ScreenShareController.java` guncellendi: offline cihaza "Yonetici ekran paylasimi talep ediyor" alert push gonderiliyor (customData: type=screen_share, sessionId). `PendingScreenShareService.java` TTL 60s → 5dk arttirildi (kullanicinin bildirime tiklamasi icin yeterli sure). Kullanici bildirime tikladiginda uygulama acilip screen share sayfasina yonleniyor
- [x] Cihaz detay sayfasindaki komut dropdown'una tum eksik MDM komutlari eklendi (toggle, text input, wallpaper modal'lari dahil). Komut dropdown boyutu ve scroll davranisi duzeltildi (`overscroll-contain`)
- [x] Cihaz detay header card'inda hizalama ve bosluk iyilestirmesi, AgentDataSection'a minimum yukseklik eklendi, sayfa yuklenirken skeleton loader eklendi
- [x] Lost Mode UI mantigi: `mdmLostModeEnabled` true oldugunda kirmizi uyari banner gosterimi ve komut listesinde yalnizca Lost Mode komutlarinin (Request Location, Play Sound, Disable Lost Mode) aktif olmasi, diger komutlarin disabled olmasi
- [x] Komut siralama bug fix: `pushCommand()` metodundan `@Async` anotasyonu kaldirildi (InMemoryAppleCommandQueueServiceImpl + RedisAppleCommandQueueServiceImpl). Eski davranista her pushCommand cagrisi bagimsiz async task olarak calisiyordu, bu nedenle ardisik pushlar (ornegin EnableLostMode + DeviceInformation) belirsiz sirada calisiyor, DeviceInformation bazen komuttan once gidiyordu. Yeni davranista pushCommand senkron calisiyor, caller metotlar zaten @Async oldugu icin FIFO siralamasi garanti ediliyor
- [x] AppleCommandSenderServiceImpl'e `queueDeviceInformationQuery()` private sync helper metodu eklendi: eskiden enableLostMode/disableLostMode/renameDevice gibi metotlar self-invocation ile queryDeviceInformation() cagiriyordu (AOP proxy bypass), yeni metot dogrudan repository'ye kayit yaparak komut siralama sorununu cozuyor
- [x] V11__Add_Location_Source.sql Flyway migrasyonu: agent_location tablosuna `source` kolonu eklendi (VARCHAR(20), default 'AGENT'). Degerler: AGENT (iOS agent MQTT), MDM_LOST_MODE (DeviceLocation komut yaniti)
- [x] AgentLocation entity'sine `source` alani eklendi (@Builder.Default "AGENT")
- [x] AgentLocationRepository'ye `findFirstByDeviceIdentifierAndSourceOrderByDeviceCreatedAtDesc()` sorgusu eklendi: kaynak bazli son konum sorgulama
- [x] AppleCommandHandlerServiceImpl.handleDeviceLocationResponse() guncellendi: apple_device_location kaydinin yaninda agent_location tablosuna da `source=MDM_LOST_MODE` ile kayit yapiliyor (cihaz detay API'sine lost mode konumu tasimak icin)
- [x] GetAppleDeviceDetailDto'ya LostModeLocationDto ic sinifi ve lostModeLocation alani eklendi (latitude, longitude, altitude, horizontalAccuracy, verticalAccuracy, speed, course, timestamp)
- [x] AppleDeviceDetailServiceImpl guncellendi: mdmLostModeEnabled true ise AgentLocationRepository'den son MDM_LOST_MODE konumunu sorgulayip LostModeLocationDto olarak response'a ekliyor
- [x] **Device Auth History**: V12 Flyway migration ile `device_auth_history` tablosu olusturuldu (device_id, identity_id, auth_source [AGENT|SETUP], event_type [SIGN_IN|SIGN_OUT], ip_address, agent_version). `DeviceAuthHistory` entity ve `DeviceAuthHistoryRepository` eklendi
- [x] **Agent Login Device Binding**: `AgentAuthController.authenticate()` basarili login sonrasi cihazi account'a bagliyor (AppleAccount bulma/olusturma, device ekleme, `AccountCreatedEvent` publish). Mevcut account'tan farkli ise eski binding kaldirilip yeni kurulur
- [x] **Agent Auth History Recording**: Her basarili login'de `AGENT/SIGN_IN` kaydı, her logout'ta `AGENT/SIGN_OUT` kaydi olusturuluyor. IP adresi X-Forwarded-For header'indan alinir
- [x] **Agent Logout Endpoint**: `POST /agent/auth/logout` — Bearer token'dan identity cozumlenir, Redis oturumu silinir, `SIGN_OUT` auth history kaydı olusturulur
- [x] **SETUP Auth History**: `AppleCheckinServiceImpl.linkAccountWithIdentity()` sonunda `SETUP/SIGN_IN` kaydı olusturuluyor (MDM enrollment sirasinda)
- [x] **AgentAuthRequest agentVersion**: Login request'ine opsiyonel `agentVersion` alani eklendi
- [x] **Device Auth History Admin API**: `GET /devices/{udid}/auth-history` paginated endpoint eklendi (`DeviceAuthHistoryController`)
- [x] **App Catalog Assignment**: V13 Flyway migration ile `app_catalog_assignment` tablosu olusturuldu (app_group_id, target_type [ACCOUNT|ACCOUNT_GROUP], target_id). `AppCatalogAssignment` entity ve repository eklendi
- [x] **AppCatalogService**: assign(), removeAssignment(), getCatalogsForAccount() (direkt + grup uzerinden), getEnrichedCatalogsForDevice() (installed/installedVersion bilgisi AppleDeviceApp'ten), isAppInCatalog() dogrulama
- [x] **AgentTokenFilter (Security)**: `/agent/catalog/**` path'leri icin `OncePerRequestFilter` — Bearer token Redis'ten dogrulanir, `agent_identity_id` request attribute'a konur. SecurityConfig filter chain'e eklendi
- [x] **App Catalog Admin API**: `AppCatalogController` — POST /app-catalogs/assign, DELETE /app-catalogs/assignments/{id}, GET /app-catalogs/app-group/{id}/assignments, GET /app-catalogs/account/{id}, GET /app-catalogs/assignments?targetType&targetId (target bazli atama listesi)
- [x] **Agent Catalog API**: `AgentCatalogController` — GET /agent/catalog (enriched catalog, installed durumu), POST /agent/catalog/install (catalog dogrulamasi + MDM InstallApplication komutu → 202 Accepted)
- [x] **Account ID Sync (back_core ↔ apple_mdm ayni UUID)**: AccountCreatedEvent publish'lere `accountId` eklendi (AppleCheckinServiceImpl, AgentAuthController, AppleAccountServiceImpl). `AccountSyncEventListener` eklendi (back_core→apple_mdm yonu, `account.core-sync.apple` queue, JdbcTemplate upsert). RabbitMQConfig'e `accountCoreSyncQueue` + `accountCoreSyncBinding` eklendi. `AgentCatalogController`'dan `backCoreClient.getBackCoreAccountIdForIdentity()` workaround kaldirildi — `account.getId()` dogrudan kullaniliyor. `BackCoreClient.getBackCoreAccountIdForIdentity()` metodu kaldirildi
- [x] **App Catalog Icon Enrichment Bug Fix**: `AppCatalogService.getCatalogsForAccount()` icon URL'leri null donuyordu cunku `AppGroupItemEntity.iconUrl` `@Transient` alan. `ItunesAppMetaRepository` ve `EnterpriseAppRepository` dependency'leri eklendi, batch icon resolve mantigi (`resolveVppIcons`, `resolveEnterpriseIcons`, `resolveIconUrl`) eklendi. VPP: `artworkUrl100` → `artworkUrl60` → `artworkUrl512` fallback. Enterprise: `data:image/png;base64,` prefix ile `iconBase64`
- [x] **Device Auth History — Device Detail Entegrasyonu**: `GetAppleDeviceDetailDto.AuthHistoryDto` inner class eklendi (id, username, authSource, eventType, ipAddress, agentVersion, createdAt). `AppleDeviceDetailServiceImpl`: `DeviceAuthHistoryRepository` dependency eklendi, `buildDetailDto()` icinde son 50 auth history kaydi fetch ediliyor (createdAt DESC)
- [x] **Dashboard API Split**: Tek `GET /dashboard/stats` endpoint'i 12 ayrı endpoint'e bölündü (`/device-stats`, `/command-stats`, `/top-apps`, `/os-versions`, `/model-distribution`, `/fleet-telemetry`, `/online-status`, `/enrollment-breakdown`, `/security-posture`, `/recent-commands`, `/device-locations`, `/command-trend`). `DashboardStatsService` build metotları `private` → `public` yapıldı. Eski `/stats` endpoint'i backward compat için korundu
- [x] **Mimari Refactoring — Logic Degismeden**: 4 fazli refactoring tamamlandi. **Faz 1**: `utils/JsonNodeUtils.java` (JSON helper'lar birlesti), `utils/HttpRequestUtils.java` (IP extraction birlesti), `services/device/DeviceLookupService.java` (merkezi UDID/serial lookup). **Faz 2**: 7 controller'dan dogrudan repository injection kaldirildi → service katmanina taşındı: `AgentAuthService` (4 repo), `AgentDeviceDataService` (3 repo), `AgentCatalogService` (2 repo), `DeviceAuthHistoryService` (2 repo), `AbmController→AppleDepService.getDeviceSummary()` (1 repo), `ScreenShareController` ve `AgentNotificationController` → `DeviceLookupService`. **Faz 3**: 6 `@Component` mapper sinifi olusturuldu: `EnrollmentAuditLogMapper`, `AppleAccountMapper`, `EnterpriseAppMapper`, `ItunesAppMapper`, `EnrollmentStatusMapper`, `AbmDeviceMapper` — service impl'lerdeki private toDto/mapToDto metotlari taşındı. **Faz 4**: `AbstractFilterSpecification<T>` base class ile 3 specification'daki %60-70 tekrar kod azaltıldı (Template Method pattern). `PolicyEventHelper` ile `ApplePolicyCreatedListener`/`ApplePolicyUpdatedListener` arasindaki ortak policy conversion/validation kodu birlestirildi. Sonuc: 0 controller repository injection, tum mapping'ler mapper class'larinda, duplicate kod %70+ azaldi
- [x] **macOS Policy Payload Modelleri + Strategy Update**: 8 yeni payload model sinifi eklendi: `FileVault.java` (com.apple.MCX.FileVault2 — enable, defer, recovery key, keychain), `Firewall.java` (com.apple.security.firewall — enable, block all incoming, stealth mode, logging, applications), `SystemExtensionPolicy.java` (com.apple.system-extension-policy — user overrides, allowed extensions by teamID, allowed types by teamID), `KernelExtensionPolicy.java` (com.apple.syspolicy.kernel-extension-policy — user overrides, team identifiers, allowed extensions by teamID), `TccPolicy.java` (com.apple.TCC.configuration-profile-policy — services map with identifier/identifierType/codeRequirement/authorization), `LoginWindow.java` (com.apple.loginwindow — text, fullName, adminHostInfo, console, input menu, password hint, shutdown/restart/sleep), `ScreenSaver.java` (com.apple.screensaver — idle time, password, delay, module path, login window idle), `EnergySaver.java` (com.apple.EnergySaver.com.apple.EnergySaver.portable — desktop/display/disk sleep timers, battery timers, wake on LAN, auto restart). **MacosPolicyPayloadStrategy**: `handleMacosConfiguration()` eklendi (systemExtensions, kernelExtensions, tccPppc, loginWindow, screensaver, energySaver). `handleSecurityConfiguration()` override edildi — super cagrisinin ustune FileVault ve Firewall payload destegi eklendi. **CommandSpecificConfigurations**: 9 yeni sabit eklendi (MACOS_CONFIGURATION, SYSTEM_EXTENSIONS, KERNEL_EXTENSIONS, TCC_PPPC, LOGIN_WINDOW, SCREENSAVER, ENERGY_SAVER, FILEVAULT, FIREWALL)

- [x] **macOS Kiosk Lockdown Destegi**: `MacosPolicyPayloadStrategy.supportsKioskLockdown()` `true` olarak guncellendi. macOS 10.14+ `com.apple.app.lock` (Single App Mode) destekler. Multi App Mode (Home Screen Layout) macOS'ta desteklenmez — sadece Single App ve Safari Domain Lock modu kullanilabilir
- [x] **App Catalog supportedPlatforms Enrichment**: `AppCatalogService.getCatalogsForAccount()` her app item'a `supportedPlatforms` listesi eklendi. VPP uygulamalar icin `ItunesAppMeta.supportedPlatforms`, Enterprise uygulamalar icin `EnterpriseApp.supportedPlatforms` kullanilir. `resolveSupportedPlatforms()` helper metodu eklendi
- [x] **Agent Catalog Platform Filtering**: `AgentCatalogServiceImpl.filterCatalogsByDevicePlatform()` eklendi — cihazin `productName` alanina gore platform etiketleri belirlenir (iPhone/iPod→iOS/iPhone, iPad→iOS/iPadOS/iPad, Mac→macOS/Mac, AppleTV→tvOS/AppleTV, Vision→visionOS, Watch→watchOS) ve katalog uygulamalari `supportedPlatforms` alanina gore filtrelenir. VPP ("iOS","macOS") ve Enterprise ("iPhone","iPad","Mac") farkli etiket formatlarini destekler
- [x] **macOS System Apps Cleanup**: macOS cihazlarda ~246 sistem/yardimci uygulamanin (SystemUIServer, TextInputMenuAgent, EscrowSecurityAlert vb.) uygulama listesinden filtrelenmesi. **commons**: `MacosUserApps.java` whitelist sinifi (~80 kullanici odakli Apple uygulama bundle ID'si, `shouldKeep()` metodu). **apple_mdm**: `handleInstalledApplicationList()` macOS cihazlarda `shouldKeep()` ile filtreleme, atlanilan uygulamalar `incomingBundleIds`'e eklenmez (sonraki sync'te orphan olarak DB'den silinir). Mevcut macOS sistem uygulamalari ve `unknown.bundle.id` kayitlari dogrudan DB'den temizlendi (prod oncesi, migration gereksiz)
- [x] **macOS Directory Service (Active Directory) Policy Destegi**: `DirectoryService.java` payload model sinifi eklendi (`com.apple.DirectoryService.managed` — hostName, userName, password, clientID, adOrganizationalUnit, adMountStyle, AD flag/value pair settings: mobile account, home local, multi-domain auth, user shell, UID/GID mapping, namespace, packet sign/encrypt, DDNS restrict, trust password interval). `MacosPolicyPayloadStrategy.handleMacosConfiguration()` icinde `DIRECTORY_SERVICE` anahtari islenip `DirectoryService.createFromMap()` ile payload olarak ekleniyor
- [x] **Dashboard Charts Expansion**: 5 yeni endpoint eklendi (`/command-analytics`, `/telemetry-analytics`, `/storage-tiers`, `/device-features`, `/enrollment-trend`). `DashboardStatsDto`'ya 11 yeni inner class (LabelCountDto, CommandTypeCountDto, CommandSuccessRateDto, CommandAnalyticsDto, TelemetryAnalyticsDto, StorageTierCountDto, FeatureEnablementDto, DeviceFeatureEnablementDto, DailyEnrollmentCountDto, EnrollmentTrendDto). Repository'lere 5 yeni native query (AppleCommandRepository: command type distribution + success rates, AppleDeviceRepository: daily enrollment counts, AppleDeviceInformationRepository: storage tiers + feature enablement). `DashboardStatsService`'e 5 yeni build metodu + 2 helper (toSortedLabelCounts, toTopN). Telemetry analytics tek iterasyonda 7 dagilimi in-memory aggregate eder (battery histogram, battery state, WiFi SSID, carrier, radio tech, language, timezone)
- [x] **TURN Server Entegrasyonu**: `ScreenShareController.java` ve `AgentPresenceService.java` guncellendi — `start_screen_share` komut payload'ina `stunServers` yaninda `turnServers` parametresi eklendi (turn:dev.uconos.com:3478). NAT arkasindaki cihazlarda WebRTC baglantisi icin TURN relay destegi
- [x] **System Setting & Account-Driven Enrollment Konfigurasyonu**: `system_setting` tablosu eklendi (V14 migration, id + operation_identifier + JSONB value + audit timestamps). `SystemSetting.java` entity, `SystemSettingRepository.java`, `SystemSettingService.java` interface ve `SystemSettingServiceImpl.java` impl olusturuldu (getValue, upsert, getByIdentifier, listAll). `SystemSettingController.java` REST controller eklendi (GET /system-settings, GET /system-settings/{id}, PUT /system-settings/{id}). `ServiceDiscoveryController.java` guncellendi — `model-family` parametresine gore `account_driven_decision` ayarindan enrollment turunu (byod/adde) okuyup sadece ilgili Version'i dondurur; ayar yoksa fallback olarak her ikisini birden dondurur
- [x] **Reports / App Analysis ozelligi**: V15 Flyway migrasyonu ile `apple_device_apps` tablosuna GIN trigram indeksleri eklendi (bundle_identifier, name, version). Yeni DTO'lar olusturuldu: `models/api/report/AppSearchResultDto`, `AppVersionDto`, `AppDeviceReportDto`, `AppDeviceReportRequestDto`. `services/report/ReportService.java` interface ve `managers/report/ReportServiceImpl.java` impl olusturuldu. `controllers/ReportController.java` 3 endpoint ile eklendi: `GET /reports/apps/search?q=&platform=` (GIN trigram bulanik uygulama arama), `GET /reports/apps/{bundleId}/versions?platform=` (bundle ID'ye gore surum dagilimi), `POST /reports/apps/{bundleId}/devices` (uygulamayi yuklu/cekilen cihazlarin sayfalanmis listesi). `AppleDeviceAppRepository`'ye 3 yeni native sorgu metodu eklendi: `searchApps` (GIN trigram ile bulanik arama), `findVersionsByBundleIdentifier` (surum + cihaz sayisi), `findDevicesByApp` (bundle ID + platform + versiyon ile sayfalanmis cihaz listesi)
- [x] **APNs p12 Password Constructor Injection Fix**: `ApplePushServiceImpl.java`'da `p12Password` field-level `@Value` injection'dan constructor injection'a cevrildi. Field injection constructor calistigindan sonra yapildigi icin `initializeApnsClient()` cagrisi sirasinda password `null` oluyordu ve `NullPointerException` veriyordu
- [x] **iPad Platform Resolution Fix**: `AppleDeviceAppRepository`'deki tum CASE WHEN platform cozumleme bloklarina `d.product_name ILIKE '%iPad%'` eklendi. iPad cihazlar artik `unknown` yerine `iOS` olarak resolve ediliyor (4 native query guncellendi)
- [x] **App Report DeviceName Enrichment**: `findDevicesByApp` native query'sine `LEFT JOIN apple_device_information di ON di.id = d.id` eklendi ve `di.device_name` select'e dahil edildi. `AppDeviceReportDto`'ya `deviceName` alani eklendi. `ReportServiceImpl.mapToDeviceReport()` mapping'ine `row[18]` → `deviceName` eklendi. Tablolarda `productName` (orn. "Mac16,12") yerine `deviceName` (orn. "Ismail's MacBook Pro") gosteriliyor
- [x] **V16 Flyway Migration**: `V16__Add_User_List_To_Device_Information.sql` — Semih'in branch'inde `AppleDeviceInformation` entity'sine eklenen `userList` JSONB alani icin eksik migration olusturuldu (Hibernate schema validation hatasini cozen migration)
- [x] **System Setting 404 Fix**: `SystemSettingServiceImpl.getByIdentifier()` metodu `orElseThrow(ResponseStatusException 404)` yerine `orElse(null)` donecek sekilde guncellendi. Henuz ayarlanmamis setting'ler icin 200 + null body donuyor (frontend'te false error state onlendi)
- [x] **3 Yeni MDM Komut Destegi (Semih branch merge)**: `DeviceCommandController`'a 3 yeni endpoint eklendi. `AppleDeviceInformation` entity'sine `userList` JSONB alani eklendi. `AppleCommandBuilderService`, `AppleCommandHandlerServiceImpl`, `AppleCommandSenderServiceImpl`'e yeni komut build/handle/send metotlari eklendi. `CommandTypes` enum'una 3 yeni tip eklendi. `InMemoryAppleCommandQueueServiceImpl` ve `RedisAppleCommandQueueServiceImpl`'e yeni queue metotlari eklendi
- [x] **Reports Backend API Spec**: Proje root'una `REPORT_BACKEND_SPEC.md` olusturuldu — 5 yeni rapor turu (Command Analysis, Fleet Health, Compliance, Enrollment, Security Posture) icin 13 endpoint, 18 DTO, SQL mantigi, Flyway migration tanimlarini iceren teknik API dokumantasyonu. Backend developer bu dosyayi referans alarak endpointleri kodlayacak
- [x] **Agent OTP Login**: Sifresi olmayan (Google Workspace, Azure Entra synced) kullanicilar icin email OTP ile agent giris destegi. `AgentAuthService`'e `sendOtp()` ve `authenticateWithOtp()` metotlari eklendi. `AgentAuthServiceImpl` refactor edildi — ortak `buildAuthResponse()` ve `findActiveIdentity()` private metotlari cikarildi. Redis `agent:otp:{email}` 5dk TTL, 6 haneli SecureRandom OTP, HTML email (JavaMailSender). `AgentAuthController`'a `POST /agent/auth/otp/send` ve `POST /agent/auth/otp/verify` endpointleri eklendi. DeviceAuthHistory authSource: "AGENT_OTP"
- [x] **Agent Activity Log**: Screen share, remote terminal ve notification islemleri icin kalici loglama. V17 Flyway migration (`agent_activity_log` tablosu — JSONB details, session_id, duration_seconds). `AgentActivityLog` entity, `AgentActivityLogRepository`, `AgentActivityLogService` interface ve `AgentActivityLogServiceImpl` olusturuldu (logStart, logComplete, logNotification, getDeviceActivityLog). `ScreenShareController`, `RemoteTerminalController`, `AgentNotificationController`'a activity log entegrasyonu eklendi. `GetAppleDeviceDetailDto`'ya `AgentActivityLogDto` nested class ve `agentActivityLog` field eklendi. `AppleDeviceDetailServiceImpl`'da son 50 activity log Device Detail'a dahil edildi
- [x] **VNC Remote Desktop (macOS)**: macOS cihazlarda browser tabanli uzak masaustu erisimi. **VncController.java**: REST API — `POST /vnc/start/{udid}` (online→200, offline+pushToken→202 waking, token yok→400), `POST /vnc/stop/{sessionId}`, `GET /vnc/status/{sessionId}`. **VncSession.java**: Redis tabanli session record (sessionId, deviceUdid, state, createdAt, updatedAt). State machine: PENDING→WAKING→AGENT_CONNECTED→VIEWER_CONNECTED→ACTIVE→ENDED. **VncSessionService.java**: Redis CRUD, TTL yonetimi (ilk 5dk, aktif 120dk). **VncTunnelHandler.java**: Binary WebSocket handler (`/vnc-tunnel/ws?session={sessionId}&role=viewer|agent`) — noVNC browser viewer ile macOS agent arasinda VNC/RFB binary frame relay. 5MB max buffer, 60s send timeout, pending message queue (race condition onleme). **WebSocketConfig.java**: Handler registration, 5MB binary buffer, CORS. **MqttMessageRouter.java**: `vnc_tunnel_ready`→session AGENT_CONNECTED, `vnc_tunnel_error`→log+end, `vnc_tunnel_stopped`→end. **AppleCommandBuilderServiceImpl**: `enableRemoteDesktop()`/`disableRemoteDesktop()` MDM komutlari. **ApplePolicyApplicationServiceImpl**: `macosConfiguration.remoteDesktop.enabled` policy handler. **CommandTypes**: DEVICE_ENABLE_REMOTE_DESKTOP_COMMAND, DEVICE_DISABLE_REMOTE_DESKTOP_COMMAND
- [x] **Bulk Device Commands**: `BulkDeviceCommandController` eklendi (`/devices/bulk/commands/{commandType}`) — birden fazla cihaza ayni anda MDM komutu gonderme. Desteklenen komut tipleri: device-information, lock, restart-device, shutdown, sync-apps. Her cihaz icin bagimsiz islem, basarisiz olanlar `FailureDetail` listesinde dondurulur. Yeni DTO'lar: `BulkCommandRequest` (udids listesi), `BulkCommandResponse` (successCount, failureCount, failures listesi, FailureDetail ic sinifi — udid, error)
- [x] **Bulk Install App**: `POST /devices/bulk/commands/install-app` endpoint'i eklendi — birden fazla cihaza VPP (trackId) veya Enterprise (identifier) uygulama kurulumu. Yeni DTO: `BulkInstallAppRequest` (udids, trackId, identifier)
- [x] **Bulk Agent Notification**: `POST /agent/notifications/bulk/send` endpoint'i eklendi (`AgentNotificationController`) — birden fazla cihaza ayni anda MQTT/APNs bildirim gonderme. Yeni DTO'lar: `BulkNotificationRequest` (udids, title, body, sound), `BulkNotificationResponse` (successCount, failureCount, failures listesi)
- [x] **Exception Handling Refactoring** — Tum service ve controller'larda exception handling standartlastirildi. `ResponseStatusException` → commons exception'lara donusturuldu: `EntityNotFoundException`, `BusinessException` (hata kodlari: VALIDATION_ERROR, VPP_ERROR, ABM_ERROR, CERT_ERROR, EMAIL_ERROR, STORAGE_ERROR, UPLOAD_ERROR, HASH_ERROR), `ConflictException`. Controller'lardaki try-catch bloklari kaldirildi, exception'lar global handler'a propagate ediliyor. Etkilenen servisler: AppleAccountServiceImpl, ItunesAppServiceImpl, EnterpriseAppServiceImpl, AppGroupServiceImpl, AppCatalogService, DeviceLookupService, AppleDeviceDetailServiceImpl, AppleVppServiceImpl, AppleDepServiceImpl, ApplePushCredentialServiceImpl, EnrollmentEmailServiceImpl. Etkilenen controller'lar: AbmController, CertificationController, EnrollmentController.
- [x] **Command Analysis Report (semihrapor branch merge)**: V20 Flyway migration ile apple_command tablosuna 6 index eklendi. 4 yeni endpoint: `GET /reports/commands/summary`, `GET /reports/commands/trend`, `GET /reports/commands/types`, `POST /reports/commands/list`. 5 yeni DTO (CommandReportSummaryDto, CommandDailyTrendDto, CommandTypeBreakdownDto, CommandReportItemDto, CommandReportRequestDto). AppleCommandRepository'ye 4 yeni native query metodu. ReportServiceImpl'e 4 yeni implementasyon
- [x] **Bulk Command Detail Paginated Devices**: `GET /reports/commands/bulk/{id}` endpoint'i artik sadece summary donuyor (devices listesi cikarildi). Yeni paginated endpoint: `GET /reports/commands/bulk/{id}/devices?page=0&size=25` — LIMIT/OFFSET ile sayfalanmis cihaz listesi, PagedModel response
- [x] **Command Report createdBy + User Stats**: `CommandReportItemDto`'ya `createdBy` eklendi, native query'ye `ac.created_by` eklendi. `GET /reports/commands/user-stats` endpoint'i top users istatistigi donuyor
- [x] **JPA Audit Fix**: Lokal `AuditorAwareImpl` (dogrudan `instanceof Jwt jwt` cast, reflection yok) + `SecurityContextConfig` (MODE_INHERITABLETHREADLOCAL) ile @Async thread'lerde created_by dogru kullanici adini yaziyor
- [x] **AsyncConfig SecurityContext Propagation**: `configs/async/AsyncConfig.java` eklendi — `AsyncConfigurer` implement ederek `ThreadPoolTaskExecutor`'u `DelegatingSecurityContextAsyncTaskExecutor` ile sarmalıyor. Bu sayede @Async metotlarda SecurityContext güvenilir şekilde propagate ediliyor ve @CreatedBy alanları "system" yerine doğru kullanıcı adını yazıyor (MODE_INHERITABLETHREADLOCAL thread pool reuse'da güvenilmezdi)
- [x] **Map Analysis Report**: `POST /reports/map-analysis/query` — CIRCLE (Haversine SQL), RECTANGLE (bounding box), POLYGON (bbox + ray-casting Java post-filter) spatial sorgulari + tarih araligi filtreleme. Device info JOIN (serial_number, product_name). Device summary aggregation, 5000 nokta limit
- [x] **4 Yeni Rapor Backend Implementasyonu**: REPORT_BACKEND_SPEC.md'deki kalan 4 rapor turu (Fleet Health, Compliance, Enrollment, Security Posture) icin 9 yeni endpoint ve 13 yeni DTO olusturuldu. **Fleet Health**: `GET /reports/fleet-health/summary` (telemetri aggregation — battery/storage histogram, thermal, network dagilimi), `POST /reports/fleet-health/devices` (sayfalanmis cihaz telemetri listesi). AgentTelemetryRepository'ye 5 yeni native query (DISTINCT ON + CTE ile en son telemetri, WIDTH_BUCKET ile histogram). **Compliance**: `GET /reports/compliance/summary` (compliant/non-compliant/no-policy sayilari, compliance_failures JSONB aggregation ile top failure reasons), `POST /reports/compliance/devices` (compliance detay listesi, policy JOIN). AppleDeviceRepository'ye 3 yeni native query. **Enrollment**: `GET /reports/enrollment/summary` (tarih araliginda enrollment/unenrollment sayilari, type distribution), `GET /reports/enrollment/trend` (gunluk enrollment CTE + UNION ile), `POST /reports/enrollment/history` (sayfalanmis enrollment history). EnrollmentHistoryRepository'ye 4 yeni native query. **Security Posture**: `GET /reports/security/summary` (supervised, activation lock, cloud backup, find my, jailbreak, vpn sayilari — device_information + telemetry JOIN), `POST /reports/security/devices` (LATERAL JOIN ile en son telemetri, filter parametresi ile security ozellik bazli filtreleme). AppleDeviceInformationRepository'ye 2 yeni native query. Tum frontend hook'lari ve type'lar ile birebir eslesme saglanarak frontend-backend entegrasyonu tamamlandi
- [x] **Active-Active / Horizontal Scaling**: Birden fazla apple-mdm instance'inin ayni anda calisabilmesi icin gerekli tum degisiklikler yapildi:
  - **MQTT Shared Subscriptions**: Tum subscribe topic'leri `$share/apple-mdm-group/{topic}` formatina donusturuldu (MqttConfig.java) — her mesaj sadece 1 instance'a iletilir
  - **MQTT Multi-Platform Topic Yapisi**: `arcops/devices/{id}/...` → `arcops/{platform}/devices/{id}/...` formatina gecildi (MqttConfig, MqttMessageRouter, tum MQTT publisher servisler)
  - **Redis Presence + Retention Lock**: `AgentPresenceService` ve `AgentRetainedMessageCleanupService`'da Redis distributed lock (`setIfAbsent` + TTL) eklendi — ayni cihaz status mesaji yalnizca 1 instance tarafindan islenir
  - **WebSocket Redis Relay**: `WebSocketRedisRelay.java` (YENİ) — Redis Pub/Sub ile cross-instance WebSocket mesaj iletimi. `RemoteTerminalHandler` ve `ScreenShareSignalingHandler` entegre edildi (subscribe/unsubscribe/publish)
  - **Command Queue Redis Default + Startup Lock**: `command.queue.type` varsayilani `redis` yapildi. `RedisAppleCommandQueueServiceImpl.loadPendingCommandsFromDatabase()` icin Redis distributed lock eklendi — sadece 1 instance DB'den yukler
  - **Instance-Aware Log Pattern**: Console log'a `${spring.application.name}:${spring.cloud.client.ip-address}:${server.port}` eklendi
- [x] **Redis Command Queue Handler Fixes (2026-03-14)**: InMemory mantigi ile hizalama yapildi. `handleDeviceErrorResponse()` icindeki PAYLOAD key path duzeltildi. `getCommandCommons()`, `sendWakeUp()`, `saveAppleCommandAsync()` metotlarinda EnrollmentID fallback eklendi (3 noktada). App inventory sync optimizasyonu yapildi
- [x] **AgentDeviceController Route Fix (2026-03-14)**: Duplicate `/api` prefix kaldirildi — `/api/agent/devices` → `/agent/devices` (tam path: `/api/apple/agent/devices`). Gateway route'u ile uyumlu hale getirildi
- [x] `logs/` dizini `.gitignore`'a eklendi
- [x] **Centralized Email Gateway Migration**: Email gonderimi `JavaMailSender` → RabbitMQ (`mail.gateway.exchange`) uzerinden back_core'daki merkezi gateway'e tasinildi. `EnrollmentEmailServiceImpl`: `JavaMailSender` → `RabbitTemplate`, HTML template aynen korundu, `SendEmailEvent` publish ediliyor. `AgentAuthServiceImpl.sendOtpEmail()`: `JavaMailSender` → `RabbitTemplate`, HTML template aynen korundu. `AppleEnrollmentWebAuthServiceImpl.sendOtpEmail()`: `JavaMailSender` → `RabbitTemplate`, HTML template aynen korundu. `spring-boot-starter-mail` dependency ve `spring.mail.*` application.yaml blogu kaldirildi
- [x] **MQTT Agent Command Tracking (2026-03-17)**: MQTT uzerinden gonderilen agent komutlari artik DB'de izleniyor (android_mdm'deki pattern ile ayni). V22 Flyway migration ile `agent_command` tablosu olusturuldu (command_uuid, device_identifier, command_type, status, payload JSONB, result JSONB, error_message, request_time, response_time). `AgentCommand` entity ve `AgentCommandRepository` eklendi. `AgentCommandService.sendCommand()` guncellendi: komut PENDING olarak kaydedilir → MQTT publish → basarili ise SENT, basarisiz ise FAILED + error message. Return tipi `String` → `AgentCommand` olarak degistirildi; `AgentDeviceController` ve `AgentNotificationController` caller'lari guncellendi. `MqttMessageRouter` guncellendi: MQTT responses'larda `commandId` alani uzerinden `AgentCommand` kaydini bulup status'u COMPLETED veya FAILED olarak guncelliyor (screen share, terminal, VNC routing korundu)
- [x] **Report & Dashboard: agent_command UNION ALL (2026-04-05)**: Rapor ve dashboard sorgulari eskiden yalnizca `apple_command` tablosunu sorguluyordu; MQTT tabanli agent komutlari (screen share, terminal, notification vb.) gorunmuyordu. Tum komut sorgularina `UNION ALL` ile `agent_command` tablosu eklendi. Status mapping: `SENT` → `PENDING` (SENT henuz yanit alinmamis demek, COMPLETED degil). Signaling mesajlari (WebRtcOffer, WebRtcIce, TerminalInput, TerminalResize, TerminalSignal, RemoteMouse, RemoteKeyboard) filtreleniyor. Degisiklikler: `AppleCommandRepository` (7 @Query — getCommandSummary, getCommandDailyTrend, getCommandTypeBreakdown, getCommandReportItems, findDailyCommandCounts, findCommandTypeDistribution, findCommandSuccessRatesByType), `DashboardStatsService` (buildCommandStats, buildRecentCommands — JdbcTemplate ile UNION ALL), `ReportController` (getCommandUserStats inline SQL). agent_command kolonu farklari: `response_time` → `completion_time`, `error_message` → `failure_reason`, `device_identifier` → `apple_device_udid`; policy_id, bulk_command_id, created_by agent_command'da yok (NULL olarak dondurulur). Tum FAILED filtreleri `IN ('FAILED', 'ERROR', 'TIMED_OUT')` olarak guncellendi — tutarli hata sayimi icin
- [x] **Stale Detection Bulk Optimizasyonu (2026-04-09)**: AgentPresenceService.detectStaleDevices() N+1 dongusu kaldirildi, batch update + batch history insert
- [x] **DisenrollService (2026-04-09)**: Server-initiated disenrollment — RemoveProfile + DEP unassign + RestartDevice. POST /devices/{udid}/disenroll ve POST /bulk-commands/disenroll endpoint'leri eklendi
- [x] **License Enforcement (2026-04-09)**: @RequiresFeature(REMOTE_ACCESS) — ScreenShareController, RemoteTerminalController, RemoteControlController, VncController. @RequiresFeature tum ReportController metotlarinda (REPORT_APP, REPORT_COMMAND, REPORT_FLEET_HEALTH, REPORT_COMPLIANCE, REPORT_ENROLLMENT, REPORT_SECURITY, REPORT_DEVICE_ACTIVITY, MAP_ANALYSIS). AppleCheckinServiceImpl'de enrollment limit kontrolu (LicenseContext + ObjectProvider). License config eklendi

---

## Geofence Sistemi (apple_mdm rolü)

apple_mdm, geofence sisteminde **MQTT köprüsü** rolü üstlenir:

### RabbitMQ Listener'lar

- **GeofenceConfigListener**: `geofence.config.apply.apple.queue` dinler. `GeofenceConfigApplyEvent` alir, location payload'larini MQTT uzerinden cihaza `update_geofences` komutu olarak gonderir.
- **GeofenceActionListener**: `geofence.action.execute.apple.queue` dinler. `GeofenceActionEvent` alir, MDM komutlari calistirir (lockDevice, enableLostMode, eraseDevice, clearPasscode).

### MQTT Event Relay

- **GeofenceEventRelayService**: MQTT `events` topic'inden gelen geofence olaylarini (geofence_enter, geofence_exit) parse eder, `GeofenceTriggeredEvent` olarak RabbitMQ'ya yayinlar.
- **MqttMessageRouter**: `MQTT_EVENTS` case'inde geofence olaylarini tespit edip GeofenceEventRelayService'e yonlendirir.

### RabbitMQ Config

- `geofence.event.exchange` (Topic), iki queue: config apply + action execute. Binding'ler `RabbitMQConfig.java` icinde tanimli.
- **Dead Letter Queue (DLQ)**: `policyApplyQueue` ve `geofenceActionExecuteQueue` icin `x-dead-letter-exchange`/`x-dead-letter-routing-key` argumanlari eklendi. Policy DLQ (`policy.dlq.exchange`/`policy.dlq.queue`) ve Geofence Action DLQ (`geofence.action.dlq.exchange`/`geofence.action.dlq.queue`) DirectExchange + durable queue + binding bean'leri tanimlandi. 5 retry sonrasi basarisiz mesajlar DLQ'ya yonlendirilir.

---

## Yapilacaklar

### Koddaki TODO'lar (Kod Icinden)

- [ ] **tvOS Kisitlama Dogrulama** -- `TvosRestrictions.java:14`: Apple dokumanlarindan dogrulanip tamamlanacak
- [ ] **watchOS Kisitlama Dogrulama** -- `WatchosRestrictions.java:14`: Apple dokumanlarindan dogrulanip tamamlanacak
- [ ] **visionOS Kisitlama Dogrulama** -- `VisionosRestrictions.java:14`: Apple dokumanlarindan dogrulanip tamamlanacak

### Son Yapilanlar

- [x] **Stale Detection Bulk Optimizasyonu** — `AgentPresenceService.detectStaleDevices()` per-device `handleOffline()` loop'u kaldirildi (N+1: her cihaz icin `findByUdid` + `calculateDurationSinceLastEvent` + save + history save). Yerine batch: `saveAll()` ile toplu device update, `presenceHistoryRepository.saveAll()` ile toplu history insert, sonra toplu RabbitMQ event publish. 10K+ cihazda olceklenme darbogazi giderildi

### Planlanan Ozellikler

- [ ] Production APNs toggle (agent push icin): Varsayilan sandbox, App Store build'leri icin `APNS_AGENT_PRODUCTION=true` / `apns.agent.production=true` ayari gerekli
- [ ] Lost Mode gelistirmeleri (otomatik etkinlestirme, bildirim)
- [ ] Enterprise uygulama OTA guncelleme
- [ ] VPP lisans yonetimi iyilestirmeleri
- [ ] Coklu tenant destegi
- [ ] ABM Token otomatik yenileme — DEP token suresi dolunca otomatik yenileme mekanizmasi yok
- [ ] Agent token TTL — Redis'teki agent oturum tokenlerinin suresi belirsiz, apple_mdm refactorde duzenlenecek
- [ ] **Cihaz bilgisi guncelleme araligi (Settings uzerinden) -- Backend** — DeviceInformation komutunun periyodik gonderim araligi Settings/konfigürasyon bazli olacak

### macOS / visionOS / tvOS / watchOS Yapilacaklar

- [ ] **visionOS Restriction Dogrulama** — `VisionosRestrictions.java:14` TODO: 38 key mevcut, Apple dokumanlarindan dogrulanip tamamlanacak
- [ ] **tvOS Restriction Dogrulama** — `TvosRestrictions.java:14` TODO: 15 key mevcut, Apple dokumanlarindan dogrulanip tamamlanacak
- [ ] **watchOS Restriction Dogrulama** — `WatchosRestrictions.java:14` TODO: 10 key mevcut, Apple dokumanlarindan dogrulanip tamamlanacak
