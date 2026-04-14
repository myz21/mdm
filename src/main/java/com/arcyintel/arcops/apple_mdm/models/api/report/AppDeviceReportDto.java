package com.arcyintel.arcops.apple_mdm.models.api.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppDeviceReportDto {
    private String deviceId;
    private String serialNumber;
    private String udid;
    private String productName;
    private String osVersion;
    private String enrollmentType;
    private String status;
    private boolean agentOnline;
    private Date agentLastSeenAt;
    private Date creationDate;
    private String platform;
    private String appName;
    private String appVersion;
    private String appShortVersion;
    private String bundleIdentifier;
    private boolean isManaged;
    private Integer bundleSize;
    private String deviceName;
}
