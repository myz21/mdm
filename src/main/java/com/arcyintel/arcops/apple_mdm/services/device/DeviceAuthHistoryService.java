package com.arcyintel.arcops.apple_mdm.services.device;

import com.arcyintel.arcops.apple_mdm.domains.DeviceAuthHistory;
import org.springframework.data.domain.Page;

/**
 * Provides access to device authentication history.
 */
public interface DeviceAuthHistoryService {

    Page<DeviceAuthHistory> getAuthHistoryByUdid(String udid, int page, int size);
}
