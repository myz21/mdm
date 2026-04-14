package com.arcyintel.arcops.apple_mdm.services.mappers;

import com.arcyintel.arcops.apple_mdm.domains.ItunesAppMeta;
import com.arcyintel.arcops.apple_mdm.models.api.itunesapp.GetItunesAppDto;
import org.springframework.stereotype.Component;

@Component
public class ItunesAppMapper {

    public GetItunesAppDto toDto(ItunesAppMeta app) {
        return GetItunesAppDto.builder()
                .id(app.getId())
                .trackId(app.getTrackId())
                .trackName(app.getTrackName())
                .bundleId(app.getBundleId())
                .version(app.getVersion())
                .description(app.getDescription())
                .releaseNotes(app.getReleaseNotes())
                .formattedPrice(app.getFormattedPrice())
                .price(app.getPrice())
                .currency(app.getCurrency())
                .primaryGenreName(app.getPrimaryGenreName())
                .averageUserRating(app.getAverageUserRating())
                .userRatingCount(app.getUserRatingCount())
                .artworkUrl512(app.getArtworkUrl512())
                .artworkUrl100(app.getArtworkUrl100())
                .artworkUrl60(app.getArtworkUrl60())
                .trackViewUrl(app.getTrackViewUrl())
                .sellerName(app.getSellerName())
                .minimumOsVersion(app.getMinimumOsVersion())
                .fileSizeBytes(app.getFileSizeBytes())
                .isVppDeviceBasedLicensingEnabled(app.getIsVppDeviceBasedLicensingEnabled())
                .totalCount(app.getTotalCount())
                .assignedCount(app.getAssignedCount())
                .availableCount(app.getAvailableCount())
                .retiredCount(app.getRetiredCount())
                .kind(app.getKind())
                .trackContentRating(app.getTrackContentRating())
                .supportedPlatforms(app.getSupportedPlatforms())
                .build();
    }
}
