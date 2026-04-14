package com.arcyintel.arcops.apple_mdm.models.api.appresolve;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppResolveRequest {

    private List<AppResolveRequestItem> apps;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppResolveRequestItem {
        private String type;      // "vpp" or "enterprise"
        private String bundleId;
    }
}
