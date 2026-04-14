package com.arcyintel.arcops.apple_mdm.services.apple.apns;

public interface ApplePushService {
    void initializeApnsClient() throws Exception;

    void sendMdmWakeUp(String token, String pushMagic);
}