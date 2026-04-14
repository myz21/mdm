package com.arcyintel.arcops.apple_mdm.models.enums;

import lombok.Getter;

@Getter
public enum PayloadIdentifiers {

    APPLOCK("com.ismailsancar.applockpayload"),
    SINGLE_APP_MODE("com.ismailsancar.singleappmode");

    private final String payloadIdentifier;

    PayloadIdentifiers(String payloadIdentifier) {
        this.payloadIdentifier = payloadIdentifier;
    }

}
