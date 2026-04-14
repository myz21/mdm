package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSString;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PerAppVpn extends BasePayload {

    private static final String PAYLOAD_TYPE = "com.apple.vpn.managed.applayer";

    // Required
    private String vpnUUID;

    // Optional
    private String[] safariDomains;
    private String[] mailDomains;
    private String[] calendarDomains;
    private String[] contactsDomains;
    private String[] associatedDomains;
    private String[] excludedDomains;
    private boolean onDemandMatchAppEnabled;
    private String[] smbDomains;
    private String cellularSliceUUID;

    public static PerAppVpn createFromMap(Map<String, Object> params, UUID policyId) {
        if (params == null) {
            return null;
        }

        PerAppVpn perAppVpn = PerAppVpn.builder()
                .vpnUUID((String) params.getOrDefault("vpnUUID", UUID.randomUUID().toString()))
                .safariDomains(toStringArray(params.get("safariDomains")))
                .mailDomains(toStringArray(params.get("mailDomains")))
                .calendarDomains(toStringArray(params.get("calendarDomains")))
                .contactsDomains(toStringArray(params.get("contactsDomains")))
                .associatedDomains(toStringArray(params.get("associatedDomains")))
                .excludedDomains(toStringArray(params.get("excludedDomains")))
                .onDemandMatchAppEnabled((boolean) params.getOrDefault("onDemandMatchAppEnabled", true))
                .smbDomains(toStringArray(params.get("smbDomains")))
                .cellularSliceUUID((String) params.getOrDefault("cellularSliceUUID", null))
                .build();

        perAppVpn.setPayloadIdentifier("com.arcyintel.arcops.perappvpn." + policyId);
        perAppVpn.setPayloadType(PAYLOAD_TYPE);
        perAppVpn.setPayloadUUID(UUID.randomUUID().toString());
        perAppVpn.setPayloadVersion(1);

        return perAppVpn;
    }

    public NSDictionary createPayload() {
        NSDictionary dict = new NSDictionary();

        // Required
        dict.put("VPNUUID", new NSString(this.getVpnUUID()));

        // Optional
        dict.put("OnDemandMatchAppEnabled", new NSNumber(this.isOnDemandMatchAppEnabled()));
        putStringArray(dict, "SafariDomains", this.safariDomains);
        putStringArray(dict, "MailDomains", this.mailDomains);
        putStringArray(dict, "CalendarDomains", this.calendarDomains);
        putStringArray(dict, "ContactsDomains", this.contactsDomains);
        putStringArray(dict, "AssociatedDomains", this.associatedDomains);
        putStringArray(dict, "ExcludedDomains", this.excludedDomains);
        putStringArray(dict, "SMBDomains", this.smbDomains);

        if (this.cellularSliceUUID != null) {
            dict.put("CellularSliceUUID", new NSString(this.cellularSliceUUID));
        }

        // BasePayload fields
        dict.put("PayloadIdentifier", new NSString(getPayloadIdentifier()));
        dict.put("PayloadType", new NSString(getPayloadType()));
        dict.put("PayloadUUID", new NSString(getPayloadUUID()));
        dict.put("PayloadVersion", new NSNumber(getPayloadVersion()));

        return dict;
    }

    private static void putStringArray(NSDictionary dict, String key, String[] values) {
        if (values != null && values.length > 0) {
            NSArray arr = new NSArray(values.length);
            for (int i = 0; i < values.length; i++) {
                arr.setValue(i, new NSString(values[i]));
            }
            dict.put(key, arr);
        }
    }

    @SuppressWarnings("unchecked")
    private static String[] toStringArray(Object obj) {
        if (obj == null) return new String[]{};
        if (obj instanceof String[] arr) return arr;
        if (obj instanceof java.util.List<?> list) {
            return list.stream().map(Object::toString).toArray(String[]::new);
        }
        return new String[]{};
    }
}