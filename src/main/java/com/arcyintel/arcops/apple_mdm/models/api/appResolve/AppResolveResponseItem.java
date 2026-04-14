package com.arcyintel.arcops.apple_mdm.models.api.appresolve;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppResolveResponseItem {
    private String bundleId;
    private String type;
    private String name;
    private String iconUrl;
    private boolean found;
}
