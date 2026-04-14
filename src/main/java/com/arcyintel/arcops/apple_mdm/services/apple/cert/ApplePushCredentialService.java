package com.arcyintel.arcops.apple_mdm.services.apple.cert;

public interface ApplePushCredentialService {

    void generateApnsP12() throws Exception;

    String getPushTopic() throws Exception;
}