package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AuditableTimestamps;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class ItunesAppMeta extends AuditableTimestamps {

    @Column(nullable = false, unique = true)
    @JsonProperty("trackId")
    private Long trackId;

    @JsonProperty("trackName")
    private String trackName;

    @JsonProperty("trackCensoredName")
    private String trackCensoredName;

    @JsonProperty("bundleId")
    private String bundleId;

    @JsonProperty("version")
    private String version;

    @Column(length = 1024 * 20)
    @JsonProperty("description")
    private String description;

    @Column(length = 1024 * 10)
    @JsonProperty("releaseNotes")
    private String releaseNotes;

    @JsonProperty("formattedPrice")
    private String formattedPrice;

    @JsonProperty("price")
    private Double price;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("primaryGenreName")
    private String primaryGenreName;

    @JsonProperty("averageUserRating")
    private Double averageUserRating;

    @JsonProperty("userRatingCount")
    private Integer userRatingCount;

    @JsonProperty("artworkUrl512")
    @Column(length = 1024)
    private String artworkUrl512;

    @JsonProperty("artworkUrl100")
    @Column(length = 1024)
    private String artworkUrl100;

    @JsonProperty("artworkUrl60")
    @Column(length = 1024)
    private String artworkUrl60;

    @JsonProperty("trackViewUrl")
    @Column(length = 1024)
    private String trackViewUrl;

    @JsonProperty("sellerName")
    private String sellerName;

    @JsonProperty("sellerUrl")
    @Column(length = 1024)
    private String sellerUrl;

    @JsonProperty("minimumOsVersion")
    private String minimumOsVersion;

    @JsonProperty("fileSizeBytes")
    private String fileSizeBytes;

    @JsonProperty("isVppDeviceBasedLicensingEnabled")
    private Boolean isVppDeviceBasedLicensingEnabled;

    @JsonProperty("totalCount")
    private int totalCount;

    @JsonProperty("assignedCount")
    private int assignedCount;

    @JsonProperty("availableCount")
    private int availableCount;

    @JsonProperty("retiredCount")
    private int retiredCount;

    @JsonProperty("isGameCenterEnabled")
    private Boolean isGameCenterEnabled;

    @JsonProperty("kind")
    private String kind;

    @JsonProperty("artistViewUrl")
    @Column(length = 1024)
    private String artistViewUrl;

    @JsonProperty("averageUserRatingForCurrentVersion")
    private Double averageUserRatingForCurrentVersion;

    @JsonProperty("trackContentRating")
    private String trackContentRating;

    @JsonProperty("contentAdvisoryRating")
    private String contentAdvisoryRating;

    @JsonProperty("wrapperType")
    private String wrapperType;

    @JsonProperty("pricingParam")
    private String pricingParam;

    // Java
    @ElementCollection
    @CollectionTable(name = "app_supported_platforms", joinColumns = @JoinColumn(name = "app_id"))
    @Column(name = "platform")
    private List<String> supportedPlatforms = new ArrayList<>();
}

