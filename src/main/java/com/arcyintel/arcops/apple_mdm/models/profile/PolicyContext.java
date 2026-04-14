package com.arcyintel.arcops.apple_mdm.models.profile;

import com.arcyintel.arcops.commons.constants.apple.ApplePlatform;
import com.arcyintel.arcops.commons.constants.apple.ManagementChannel;

public record PolicyContext(
        ApplePlatform platform,
        ManagementChannel channel
) {
    public boolean isByod() {
        return channel.isByod();
    }

    public boolean isSupervised() {
        return channel.isFullyManaged();
    }

    public boolean isIosFamily() {
        return platform.isIosFamily();
    }
}
