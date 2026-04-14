package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AuditableTimestamps;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;

import java.util.List;

@Entity
@Table(name = "abm_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AbmProfile extends AuditableTimestamps {

    @Column(name = "profile_uuid", nullable = false, unique = true)
    private String profileUuid;

    @Column(name = "profile_name")
    private String profileName;

    @Column
    private String url;

    @Column(name = "configuration_web_url")
    private String configurationWebUrl;

    @Column(name = "allow_pairing")
    private Boolean allowPairing;

    @Column(name = "is_supervised")
    private Boolean isSupervised;

    @Column(name = "is_multi_user")
    private Boolean isMultiUser;

    @Column(name = "is_mandatory")
    private Boolean isMandatory;

    @Column(name = "await_device_configured")
    private Boolean awaitDeviceConfigured;

    @Column(name = "is_mdm_removable")
    private Boolean isMdmRemovable;

    @Column(name = "auto_advance_setup")
    private Boolean autoAdvanceSetup;

    @Column(name = "support_phone_number")
    private String supportPhoneNumber;

    @Column(name = "support_email_address")
    private String supportEmailAddress;

    @Column(name = "org_magic")
    private String orgMagic;

    @Column
    private String department;

    @Column
    private String language;

    @Column
    private String region;

    @Type(JsonType.class)
    @Column(name = "skip_setup_items", columnDefinition = "jsonb")
    private List<String> skipSetupItems;

    @Type(JsonType.class)
    @Column(name = "anchor_certs", columnDefinition = "jsonb")
    private List<String> anchorCerts;

    @Type(JsonType.class)
    @Column(name = "supervising_host_certs", columnDefinition = "jsonb")
    private List<String> supervisingHostCerts;
}