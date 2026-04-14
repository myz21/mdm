package com.arcyintel.arcops.apple_mdm.services.app;

import com.arcyintel.arcops.apple_mdm.models.api.itunesapp.GetItunesAppDto;
import com.arcyintel.arcops.commons.api.DynamicListRequestDto;
import org.springframework.data.web.PagedModel;

import java.util.List;
import java.util.Map;

public interface ItunesAppService {
    GetItunesAppDto getApp(String id);
    GetItunesAppDto getAppByTrackId(Long trackId);
    GetItunesAppDto getAppByBundleId(String bundleId);
    PagedModel<Map<String, Object>> listApps(DynamicListRequestDto request);
    List<String> getDistinctGenres();
    Map<String, Long> getGenreCounts();
}
