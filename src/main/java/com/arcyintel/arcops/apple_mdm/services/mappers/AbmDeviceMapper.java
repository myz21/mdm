package com.arcyintel.arcops.apple_mdm.services.mappers;

import com.arcyintel.arcops.apple_mdm.domains.AbmDevice;
import com.arcyintel.arcops.apple_mdm.models.api.device.AbmDeviceSummaryDto;
import org.springframework.stereotype.Component;

@Component
public class AbmDeviceMapper {

    public AbmDeviceSummaryDto.RecentDevice toRecentDevice(AbmDevice d) {
        return AbmDeviceSummaryDto.RecentDevice.builder()
                .serialNumber(d.getSerialNumber())
                .model(d.getModel())
                .description(d.getDescription())
                .color(d.getColor())
                .os(d.getOs())
                .deviceFamily(d.getDeviceFamily())
                .profileStatus(d.getProfileStatus())
                .deviceAssignedDate(d.getDeviceAssignedDate())
                .build();
    }
}
