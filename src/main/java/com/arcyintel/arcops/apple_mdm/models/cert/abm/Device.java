package com.arcyintel.arcops.apple_mdm.models.cert.abm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Device {

    @JsonProperty("serial_number")
    private String serialNumber;

    @JsonProperty("model")
    private String model;

    @JsonProperty("description")
    private String description;

    @JsonProperty("color")
    private String color;

    @JsonProperty("asset_tag")
    private String assetTag;

    @JsonProperty("profile_status")
    private String profileStatus;

    @JsonProperty("profile_uuid")
    private String profileUUID;

    @JsonProperty("profile_assign_time")
    private String profileAssignTime;

    @JsonProperty("profile_push_time")
    private String profilePushTime;

    @JsonProperty("device_assigned_date")
    private String deviceAssignedDate;

    @JsonProperty("device_assigned_by")
    private String deviceAssignedBy;

    @JsonProperty("os")
    private String os;

    @JsonProperty("device_family")
    private String deviceFamily;

    @JsonProperty("op_type")
    private String opType;

    @JsonProperty("op_date")
    private String opDate;

    @JsonProperty("response_status")
    private String responseStatus;
}