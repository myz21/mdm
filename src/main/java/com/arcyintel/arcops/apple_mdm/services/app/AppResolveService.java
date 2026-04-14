package com.arcyintel.arcops.apple_mdm.services.app;

import com.arcyintel.arcops.apple_mdm.models.api.appresolve.AppResolveRequest.AppResolveRequestItem;
import com.arcyintel.arcops.apple_mdm.models.api.appresolve.AppResolveResponseItem;

import java.util.List;

public interface AppResolveService {
    List<AppResolveResponseItem> resolveApps(List<AppResolveRequestItem> apps);
}
