package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSString;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AirPrint extends BasePayload {

    private List<AirPrintItem> airPrint;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AirPrintItem {
        private String ipAddress;
        private String resourcePath;
        private Integer port;
        private Boolean forceTLS;
    }

    @SuppressWarnings("unchecked")
    public static AirPrint createFromMap(Map<String, Object> map, UUID policyId) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) map.get("airPrintList");
        List<AirPrintItem> airPrintItems = new ArrayList<>();

        if (items != null) {
            for (Map<String, Object> item : items) {
                airPrintItems.add(AirPrintItem.builder()
                        .ipAddress((String) item.get("IPAddress"))
                        .resourcePath((String) item.get("ResourcePath"))
                        .port(item.get("Port") instanceof Number n ? n.intValue() : null)
                        .forceTLS(item.get("ForceTLS") instanceof Boolean b ? b : null)
                        .build());
            }
        }

        AirPrint payload = AirPrint.builder()
                .airPrint(airPrintItems)
                .build();
        payload.setPayloadIdentifier("com.apple.airprint." + policyId);
        payload.setPayloadType("com.apple.airprint");
        payload.setPayloadUUID(UUID.randomUUID().toString());
        payload.setPayloadVersion(1);
        return payload;
    }

    public NSDictionary createPayload() {
        NSDictionary d = new NSDictionary();
        int size = airPrint != null ? airPrint.size() : 0;
        NSArray airPrintArray = new NSArray(size);
        for (int i = 0; i < size; i++) {
            AirPrintItem item = airPrint.get(i);
            NSDictionary itemDict = new NSDictionary();
            if (item.getIpAddress() != null) {
                itemDict.put("IPAddress", new NSString(item.getIpAddress()));
            }
            if (item.getResourcePath() != null) {
                itemDict.put("ResourcePath", new NSString(item.getResourcePath()));
            }
            if (item.getPort() != null) {
                itemDict.put("Port", new NSNumber(item.getPort()));
            }
            if (item.getForceTLS() != null) {
                itemDict.put("ForceTLS", new NSNumber(item.getForceTLS()));
            }
            airPrintArray.setValue(i, itemDict);
        }
        d.put("AirPrint", airPrintArray);
        d.put("PayloadIdentifier", new NSString(getPayloadIdentifier()));
        d.put("PayloadType", new NSString(getPayloadType()));
        d.put("PayloadUUID", new NSString(getPayloadUUID()));
        d.put("PayloadVersion", new NSNumber(getPayloadVersion()));
        return d;
    }
}