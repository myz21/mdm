package com.arcyintel.arcops.apple_mdm.models.api.itunesapp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetItunesAppDto {
    private UUID id;
    private Long trackId;
    private String trackName;
    private String bundleId;
    private String version;
    private String description;
    private String releaseNotes;
    private String formattedPrice;
    private Double price;
    private String currency;
    private String primaryGenreName;
    private Double averageUserRating;
    private Integer userRatingCount;
    private String artworkUrl512;
    private String artworkUrl100;
    private String artworkUrl60;
    private String trackViewUrl;
    private String sellerName;
    private String minimumOsVersion;
    private String fileSizeBytes;
    private Boolean isVppDeviceBasedLicensingEnabled;
    private int totalCount;
    private int assignedCount;
    private int availableCount;
    private int retiredCount;
    private String kind;
    private String trackContentRating;
    private List<String> supportedPlatforms;
}
