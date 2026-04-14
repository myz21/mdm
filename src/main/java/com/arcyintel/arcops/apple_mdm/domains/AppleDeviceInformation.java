package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AuditableTimestamps;


import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@ToString(exclude = {"securityInfo", "certificateList", "userList"})
@Entity
@Table(name = "apple_device_information")
public class AppleDeviceInformation extends AuditableTimestamps {

    @Column(name = "app_analytics_enabled")
    private Boolean appAnalyticsEnabled;

    @Column(name = "awaiting_configuration")
    private Boolean awaitingConfiguration;

    @Column(name = "battery_level")
    private Number batteryLevel;

    @Column(name = "bluetoothmac")
    private String bluetoothMAC;

    @Column(name = "build_version")
    private String buildVersion;

    @Column(name = "cellular_technology")
    private Integer cellularTechnology;

    @Column(name = "data_roaming_enabled")
    private Boolean dataRoamingEnabled;

    @Column(name = "device_capacity")
    private Number deviceCapacity;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "diagnostic_submission_enabled")
    private Boolean diagnosticSubmissionEnabled;

    @Column(name = "eas_device_identifier")
    private String easDeviceIdentifier;

    @Column(name = "imei")
    private String imei;

    @Column(name = "activation_lock_enabled")
    private Boolean activationLockEnabled;

    @Column(name = "cloud_backup_enabled")
    private Boolean cloudBackupEnabled;

    @Column(name = "device_locator_service_enabled")
    private Boolean deviceLocatorServiceEnabled;

    @Column(name = "do_not_disturb_in_effect")
    private Boolean doNotDisturbInEffect;

    @Column(name = "mdmlost_mode_enabled")
    private Boolean mdmlostModeEnabled;

    @Column(name = "multi_user")
    private Boolean multiUser;

    @Column(name = "network_tethered")
    private Boolean networkTethered;

    @Column(name = "roaming")
    private Boolean roaming;

    @Column(name = "supervised")
    private Boolean supervised;

    @Column(name = "itunes_store_account_hash")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> itunesStoreAccountHash;

    @Column(name = "meid")
    private String meid;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "modem_firmware_version")
    private String modemFirmwareVersion;

    @Column(name = "os_version")
    private String osVersion;

    @Column(name = "personal_hotspot_enabled")
    private Boolean personalHotspotEnabled;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "service_subscriptions")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> serviceSubscriptions;

    @Column(name = "subscribermcc")
    private String subscriberMCC;

    @Column(name = "subscribermnc")
    private String subscriberMNC;

    @Column(name = "udid")
    private String udid;

    @Column(name = "voice_roaming_enabled")
    private Boolean voiceRoamingEnabled;

    @Column(name = "wifimac")
    private String wifiMAC;

    @Column(name = "itunes_store_account_is_active")
    private Boolean itunesStoreAccountIsActive;

    @Column(name = "model", length = Integer.MAX_VALUE)
    private String model;

    @Column(name = "mdm_options")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> mdmOptions;

    @Column(name = "security_info")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> securityInfo;

    @Column(name = "certificate_list")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Map<String, Object>> certificateList;

    @Column(name = "user_list")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Map<String, Object>> userList;

    @Column(name = "user_channel")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> userChannel;

    @OneToOne(fetch = FetchType.EAGER, targetEntity = AppleDevice.class)
    @JoinColumn(name = "id", referencedColumnName = "id")
    @MapsId
    @JsonBackReference
    private AppleDevice appleDevice;

    /**
     * AppleDeviceInformation nesnesini Map'e dönüştürür
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();

        map.put("deviceName", deviceName);
        map.put("modelName", modelName);
        map.put("productName", productName);
        map.put("udid", udid);
        map.put("imei", imei);
        map.put("meid", meid);
        map.put("easDeviceIdentifier", easDeviceIdentifier);
        map.put("deviceCapacity", deviceCapacity);
        map.put("batteryLevel", batteryLevel);
        map.put("awaitingConfiguration", awaitingConfiguration);
        map.put("supervised", supervised);
        map.put("multiUser", multiUser);
        map.put("activationLockEnabled", activationLockEnabled);
        map.put("mdmlostModeEnabled", mdmlostModeEnabled);
        map.put("bluetoothMAC", bluetoothMAC);
        map.put("itunesStoreAccountIsActive", itunesStoreAccountIsActive);
        map.put("itunesStoreAccountHash", itunesStoreAccountHash);
        map.put("wifiMAC", wifiMAC);
        map.put("cellularTechnology", cellularTechnology);
        map.put("dataRoamingEnabled", dataRoamingEnabled);
        map.put("voiceRoamingEnabled", voiceRoamingEnabled);
        map.put("roaming", roaming);
        map.put("subscriberMCC", subscriberMCC);
        map.put("subscriberMNC", subscriberMNC);
        map.put("modemFirmwareVersion", modemFirmwareVersion);
        map.put("networkTethered", networkTethered);
        map.put("personalHotspotEnabled", personalHotspotEnabled);
        map.put("osVersion", osVersion);
        map.put("buildVersion", buildVersion);
        map.put("diagnosticSubmissionEnabled", diagnosticSubmissionEnabled);
        map.put("appAnalyticsEnabled", appAnalyticsEnabled);
        map.put("doNotDisturbInEffect", doNotDisturbInEffect);
        map.put("cloudBackupEnabled", cloudBackupEnabled);
        map.put("deviceLocatorServiceEnabled", deviceLocatorServiceEnabled);
        map.put("securityInfo", securityInfo);
        map.put("certificateList", certificateList);
        map.put("userList", userList);

        return map;
    }
}