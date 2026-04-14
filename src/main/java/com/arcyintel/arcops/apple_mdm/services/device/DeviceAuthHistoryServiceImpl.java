package com.arcyintel.arcops.apple_mdm.services.device;

import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.domains.DeviceAuthHistory;
import com.arcyintel.arcops.apple_mdm.repositories.DeviceAuthHistoryRepository;
import com.arcyintel.arcops.apple_mdm.services.device.DeviceAuthHistoryService;
import com.arcyintel.arcops.apple_mdm.services.device.DeviceLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceAuthHistoryServiceImpl implements DeviceAuthHistoryService {

    private final DeviceAuthHistoryRepository deviceAuthHistoryRepository;
    private final DeviceLookupService deviceLookupService;

    @Override
    public Page<DeviceAuthHistory> getAuthHistoryByUdid(String udid, int page, int size) {
        AppleDevice device = deviceLookupService.getByUdid(udid);
        return deviceAuthHistoryRepository.findByDevice_Id(
                device.getId(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
