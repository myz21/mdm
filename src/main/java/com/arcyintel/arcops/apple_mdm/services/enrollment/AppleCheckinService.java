package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.dd.plist.NSDictionary;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;

import java.util.Map;
import java.util.UUID;

public interface AppleCheckinService {

    AppleDevice authenticate(NSDictionary dict, String orgMagic);


    AppleDevice tokenUpdate(NSDictionary dict) throws Exception;


    AppleDevice checkOut(NSDictionary dict);


    String declarativeManagement(NSDictionary dict) throws Exception;


    Map<String, Object> getDeclarativeAssetDocument(UUID deviceId, String assetIdentifier);
}