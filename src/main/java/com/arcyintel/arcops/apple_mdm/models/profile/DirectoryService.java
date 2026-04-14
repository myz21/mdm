package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSString;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectoryService extends BasePayload {

    private static final String PAYLOAD_TYPE = "com.apple.DirectoryService.managed";

    // Required
    private String hostName;

    // Identity
    private String userName;
    private String password;
    private String clientID;
    private String description;
    private String adOrganizationalUnit;

    // Network
    private String adMountStyle; // "afp" or "smb"

    // Flag/Value pair settings
    private Boolean adCreateMobileAccountAtLogin;
    private Boolean adWarnUserBeforeCreatingMA;
    private Boolean adForceHomeLocal;
    private Boolean adUseWindowsUNCPath;
    private Boolean adAllowMultiDomainAuth;
    private String adDefaultUserShell;
    private String adMapUIDAttribute;
    private String adMapGIDAttribute;
    private String adMapGGIDAttribute;
    private String adPreferredDCServer;
    private List<String> adDomainAdminGroupList;
    private String adNamespace; // "forest" or "domain"
    private String adPacketSign; // "allow", "disable", "require"
    private String adPacketEncrypt; // "allow", "disable", "require", "ssl"
    private List<String> adRestrictDDNS;
    private Integer adTrustChangePassIntervalDays;

    @SuppressWarnings("unchecked")
    public static DirectoryService createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }

        DirectoryService ds = DirectoryService.builder()
                .hostName((String) map.get("hostName"))
                .userName((String) map.get("userName"))
                .password((String) map.get("password"))
                .clientID((String) map.get("clientID"))
                .description((String) map.get("description"))
                .adOrganizationalUnit((String) map.get("adOrganizationalUnit"))
                .adMountStyle((String) map.get("adMountStyle"))
                .adCreateMobileAccountAtLogin((Boolean) map.get("adCreateMobileAccountAtLogin"))
                .adWarnUserBeforeCreatingMA((Boolean) map.get("adWarnUserBeforeCreatingMA"))
                .adForceHomeLocal((Boolean) map.get("adForceHomeLocal"))
                .adUseWindowsUNCPath((Boolean) map.get("adUseWindowsUNCPath"))
                .adAllowMultiDomainAuth((Boolean) map.get("adAllowMultiDomainAuth"))
                .adDefaultUserShell((String) map.get("adDefaultUserShell"))
                .adMapUIDAttribute((String) map.get("adMapUIDAttribute"))
                .adMapGIDAttribute((String) map.get("adMapGIDAttribute"))
                .adMapGGIDAttribute((String) map.get("adMapGGIDAttribute"))
                .adPreferredDCServer((String) map.get("adPreferredDCServer"))
                .adDomainAdminGroupList((List<String>) map.get("adDomainAdminGroupList"))
                .adNamespace((String) map.get("adNamespace"))
                .adPacketSign((String) map.get("adPacketSign"))
                .adPacketEncrypt((String) map.get("adPacketEncrypt"))
                .adRestrictDDNS((List<String>) map.get("adRestrictDDNS"))
                .adTrustChangePassIntervalDays(map.get("adTrustChangePassIntervalDays") != null
                        ? ((Number) map.get("adTrustChangePassIntervalDays")).intValue() : null)
                .build();

        ds.setPayloadIdentifier("com.arcyintel.arcops.directoryservice." + policyId);
        ds.setPayloadType(PAYLOAD_TYPE);
        ds.setPayloadUUID(UUID.randomUUID().toString());
        ds.setPayloadVersion(1);
        ds.setPayloadRemovalDisallowed(true);

        return ds;
    }

    public NSDictionary createPayload() {
        NSDictionary payload = new NSDictionary();

        // Standard payload fields
        payload.put("PayloadType", new NSString(getPayloadType()));
        payload.put("PayloadVersion", new NSNumber(getPayloadVersion()));
        payload.put("PayloadIdentifier", new NSString(getPayloadIdentifier()));
        payload.put("PayloadUUID", new NSString(getPayloadUUID()));
        payload.put("PayloadRemovalDisallowed", new NSNumber(getPayloadRemovalDisallowed()));

        // Required
        if (hostName != null) {
            payload.put("HostName", new NSString(hostName));
        }

        // Identity
        if (userName != null) {
            payload.put("UserName", new NSString(userName));
        }
        if (password != null) {
            payload.put("Password", new NSString(password));
        }
        if (clientID != null) {
            payload.put("ClientID", new NSString(clientID));
        }
        if (description != null) {
            payload.put("Description", new NSString(description));
        }
        if (adOrganizationalUnit != null) {
            payload.put("ADOrganizationalUnit", new NSString(adOrganizationalUnit));
        }

        // Network
        if (adMountStyle != null) {
            payload.put("ADMountStyle", new NSString(adMountStyle));
        }

        // Flag/Value pair settings — if value is non-null, set flag to true and include value
        if (adCreateMobileAccountAtLogin != null) {
            payload.put("ADCreateMobileAccountAtLoginFlag", new NSNumber(true));
            payload.put("ADCreateMobileAccountAtLogin", new NSNumber(adCreateMobileAccountAtLogin));
        }
        if (adWarnUserBeforeCreatingMA != null) {
            payload.put("ADWarnUserBeforeCreatingMAFlag", new NSNumber(true));
            payload.put("ADWarnUserBeforeCreatingMA", new NSNumber(adWarnUserBeforeCreatingMA));
        }
        if (adForceHomeLocal != null) {
            payload.put("ADForceHomeLocalFlag", new NSNumber(true));
            payload.put("ADForceHomeLocal", new NSNumber(adForceHomeLocal));
        }
        if (adUseWindowsUNCPath != null) {
            payload.put("ADUseWindowsUNCPathFlag", new NSNumber(true));
            payload.put("ADUseWindowsUNCPath", new NSNumber(adUseWindowsUNCPath));
        }
        if (adAllowMultiDomainAuth != null) {
            payload.put("ADAllowMultiDomainAuthFlag", new NSNumber(true));
            payload.put("ADAllowMultiDomainAuth", new NSNumber(adAllowMultiDomainAuth));
        }
        if (adDefaultUserShell != null) {
            payload.put("ADDefaultUserShellFlag", new NSNumber(true));
            payload.put("ADDefaultUserShell", new NSString(adDefaultUserShell));
        }
        if (adMapUIDAttribute != null) {
            payload.put("ADMapUIDAttributeFlag", new NSNumber(true));
            payload.put("ADMapUIDAttribute", new NSString(adMapUIDAttribute));
        }
        if (adMapGIDAttribute != null) {
            payload.put("ADMapGIDAttributeFlag", new NSNumber(true));
            payload.put("ADMapGIDAttribute", new NSString(adMapGIDAttribute));
        }
        if (adMapGGIDAttribute != null) {
            payload.put("ADMapGGIDAttributeFlag", new NSNumber(true));
            payload.put("ADMapGGIDAttribute", new NSString(adMapGGIDAttribute));
        }
        if (adPreferredDCServer != null) {
            payload.put("ADPreferredDCServerFlag", new NSNumber(true));
            payload.put("ADPreferredDCServer", new NSString(adPreferredDCServer));
        }
        if (adDomainAdminGroupList != null && !adDomainAdminGroupList.isEmpty()) {
            payload.put("ADDomainAdminGroupListFlag", new NSNumber(true));
            NSArray groupArray = new NSArray(adDomainAdminGroupList.size());
            for (int i = 0; i < adDomainAdminGroupList.size(); i++) {
                groupArray.setValue(i, new NSString(adDomainAdminGroupList.get(i)));
            }
            payload.put("ADDomainAdminGroupList", groupArray);
        }
        if (adNamespace != null) {
            payload.put("ADNamespaceFlag", new NSNumber(true));
            payload.put("ADNamespace", new NSString(adNamespace));
        }
        if (adPacketSign != null) {
            payload.put("ADPacketSignFlag", new NSNumber(true));
            payload.put("ADPacketSign", new NSString(adPacketSign));
        }
        if (adPacketEncrypt != null) {
            payload.put("ADPacketEncryptFlag", new NSNumber(true));
            payload.put("ADPacketEncrypt", new NSString(adPacketEncrypt));
        }
        if (adRestrictDDNS != null && !adRestrictDDNS.isEmpty()) {
            payload.put("ADRestrictDDNSFlag", new NSNumber(true));
            NSArray ddnsArray = new NSArray(adRestrictDDNS.size());
            for (int i = 0; i < adRestrictDDNS.size(); i++) {
                ddnsArray.setValue(i, new NSString(adRestrictDDNS.get(i)));
            }
            payload.put("ADRestrictDDNS", ddnsArray);
        }
        if (adTrustChangePassIntervalDays != null) {
            payload.put("ADTrustChangePassIntervalDaysFlag", new NSNumber(true));
            payload.put("ADTrustChangePassIntervalDays", new NSNumber(adTrustChangePassIntervalDays));
        }

        return payload;
    }
}
