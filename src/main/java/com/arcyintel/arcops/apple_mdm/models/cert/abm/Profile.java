package com.arcyintel.arcops.apple_mdm.models.cert.abm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

// via: https://developer.apple.com/documentation/devicemanagement/profile
@Data
public class Profile {

    @JsonProperty("profile_name")
    private String profileName;

    @JsonProperty("profile_uuid")
    private String profileUUID;

    @JsonProperty("url")
    private String url;

    @JsonProperty("allow_pairing")
    private Boolean allowPairing;

    @JsonProperty("is_supervised")
    private Boolean isSupervised;

    @JsonProperty("is_multi_user")
    private Boolean isMultiUser;

    @JsonProperty("is_mandatory")
    private Boolean isMandatory;

    @JsonProperty("await_device_configured")
    private Boolean awaitDeviceConfigured;

    @JsonProperty("is_mdm_removable")
    private Boolean isMDMRemovable;

    @JsonProperty("support_phone_number")
    private String supportPhoneNumber;

    @JsonProperty("support_email_address")
    private String supportEmailAddress;

    @JsonProperty("auto_advance_setup")
    private Boolean autoAdvanceSetup;

    @JsonProperty("org_magic")
    private String orgMagic;

    @JsonProperty("anchor_certs")
    private List<String> anchorCerts;

    @JsonProperty("supervising_host_certs")
    private List<String> supervisingHostCerts;

    @JsonProperty("skip_setup_items")
    private List<String> skipSetupItems;

    @JsonProperty("department")
    private String department;

    @JsonProperty("devices")
    private List<String> devices;

    @JsonProperty("language")
    private String language;

    @JsonProperty("region")
    private String region;

    @JsonProperty("configuration_web_url")
    private String configurationWebURL;

    /**
     * Frontend-only flag: when true, backend auto-sets configuration_web_url.
     * Not sent to Apple API (excluded via @JsonIgnore on serialization to Apple).
     */
    @JsonProperty("use_web_auth")
    private Boolean useWebAuth;
}