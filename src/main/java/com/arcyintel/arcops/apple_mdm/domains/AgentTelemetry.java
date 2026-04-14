package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AbstractEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Stores a single telemetry snapshot received from a device agent via MQTT.
 * Each row represents one point-in-time measurement of device metrics.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Table(name = "agent_telemetry")
public class AgentTelemetry extends AbstractEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private AppleDevice device;

    @Column(name = "device_identifier", nullable = false)
    private String deviceIdentifier;

    @Column(name = "device_created_at", nullable = false)
    private Instant deviceCreatedAt;

    @Column(name = "server_received_at", nullable = false)
    @Builder.Default
    private Instant serverReceivedAt = Instant.now();

    // Battery
    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Column(name = "battery_charging")
    private Boolean batteryCharging;

    @Column(name = "battery_state", length = 20)
    private String batteryState;

    @Column(name = "low_power_mode")
    private Boolean lowPowerMode;

    // Storage
    @Column(name = "storage_total_bytes")
    private Long storageTotalBytes;

    @Column(name = "storage_free_bytes")
    private Long storageFreeBytes;

    @Column(name = "storage_used_bytes")
    private Long storageUsedBytes;

    @Column(name = "storage_usage_percent")
    private Integer storageUsagePercent;

    // Memory
    @Column(name = "memory_total_bytes")
    private Long memoryTotalBytes;

    @Column(name = "memory_available_bytes")
    private Long memoryAvailableBytes;

    // System
    @Column(name = "system_uptime")
    private Integer systemUptime;

    @Column(name = "cpu_cores")
    private Integer cpuCores;

    @Column(name = "thermal_state", length = 20)
    private String thermalState;

    @Column(name = "brightness")
    private Integer brightness;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(name = "model_identifier", length = 50)
    private String modelIdentifier;

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    // Network
    @Column(name = "network_type", length = 20)
    private String networkType;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "is_expensive")
    private Boolean isExpensive;

    @Column(name = "is_constrained")
    private Boolean isConstrained;

    @Column(name = "vpn_active")
    private Boolean vpnActive;

    @Column(name = "wifi_ssid", length = 255)
    private String wifiSsid;

    @Column(name = "carrier_name", length = 100)
    private String carrierName;

    @Column(name = "radio_technology", length = 20)
    private String radioTechnology;

    // Security
    @Column(name = "jailbreak_detected")
    private Boolean jailbreakDetected;

    @Column(name = "debugger_attached")
    private Boolean debuggerAttached;

    // Locale
    @Column(name = "locale_language", length = 10)
    private String localeLanguage;

    @Column(name = "locale_region", length = 10)
    private String localeRegion;

    @Column(name = "locale_timezone", length = 100)
    private String localeTimezone;
}
