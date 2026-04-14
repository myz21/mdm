package com.arcyintel.arcops.apple_mdm.models.enums;

import lombok.Getter;

@Getter
public enum CheckInTypes {

    Authenticate("Authenticate"),
    TokenUpdate("TokenUpdate"),
    CheckOut("CheckOut"),
    DeclarativeManagement("DeclarativeManagement"),
    SetBootstrapToken("SetBootstrapToken"),
    GetBootstrapToken("GetBootstrapToken");

    private final String type;

    CheckInTypes(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return type;
    }
}
