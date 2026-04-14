package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSDictionary;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WebContentFilter extends BasePayload {


    // Genel
    private String filterType = "BuiltIn"; // BuiltIn | Plugin
    private String userDefinedName;        // Required when FilterType = Plugin

    // Built-in tarafı
    private Boolean autoFilterEnabled = false;
    private List<String> denyListURLs;
    private List<String> permittedURLs;
    private List<Map<String, Object>> allowListBookmarks; // AllowListBookmarksItem
    private Boolean safariHistoryRetentionEnabled = true;
    private Boolean hideDenyListURLs = false;

    // Plugin tarafı – Plugin tipinde kullanılacaklar
    private Boolean filterBrowsers = false;
    private Boolean filterSockets = false;
    private Boolean filterPackets = false;
    private Boolean filterURLs = false; // iOS 16 / macOS 13 ve sonrası

    private String filterGrade = "firewall"; // firewall | inspector

    private String pluginBundleID; // PluginBundleID

    private String contentFilterUUID; // ContentFilterUUID

    private String filterDataProviderBundleIdentifier;
    private String filterDataProviderDesignatedRequirement;

    private String filterPacketProviderBundleIdentifier;
    private String filterPacketProviderDesignatedRequirement;

    private String organization;
    private String userName;
    private String password;
    private String serverAddress;

    private String payloadCertificateUUID; // PayloadCertificateUUID

    // iOS 16+ URL filter params ve vendor config
    private Map<String, Object> urlFilterParameters; // URLFilterParameters
    private Map<String, Object> vendorConfig;        // VendorConfig


    /**
     * Creates a BuiltIn WebContentFilter that only permits the given domain (and optionally its subdomains).
     */
    public static WebContentFilter createForDomainLock(String domain, boolean allowSubdomains, UUID policyId) {
        List<String> permittedURLs = new java.util.ArrayList<>();
        permittedURLs.add("https://" + domain);
        permittedURLs.add("http://" + domain);
        if (allowSubdomains) {
            permittedURLs.add("https://*." + domain);
            permittedURLs.add("http://*." + domain);
        }
        return createFromMap(Map.of(
                "filterType", "BuiltIn",
                "permittedURLs", permittedURLs,
                "autoFilterEnabled", false
        ), policyId);
    }

    @SuppressWarnings("unchecked")
    public static WebContentFilter createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }

        WebContentFilter.WebContentFilterBuilder builder = WebContentFilter.builder();

        // FilterType (BuiltIn / Plugin)
        String filterType = map.getOrDefault("filterType", "BuiltIn").toString();
        builder.filterType(filterType);

        // Ortak alanlar
        Object userDefinedNameObj = map.get("userDefinedName");
        if (userDefinedNameObj != null) {
            builder.userDefinedName(userDefinedNameObj.toString());
        }

        // BuiltIn konfig (AutoFilterEnabled, DenylistURLs, PermittedURLs vs.)
        builder.autoFilterEnabled((Boolean) map.getOrDefault("autoFilterEnabled", false));
        builder.safariHistoryRetentionEnabled((Boolean) map.getOrDefault("safariHistoryRetentionEnabled", true));
        builder.hideDenyListURLs((Boolean) map.getOrDefault("hideDenyListURLs", false));

        Object denyListObj = map.get("denyListURLs");
        if (denyListObj instanceof List<?> list) {
            builder.denyListURLs(list.stream().filter(Objects::nonNull).map(Object::toString).toList());
        }

        Object permittedObj = map.get("permittedURLs");
        if (permittedObj instanceof List<?> list) {
            builder.permittedURLs(list.stream().filter(Objects::nonNull).map(Object::toString).toList());
        }

        Object allowBookmarksObj = map.get("allowListBookmarks");
        if (allowBookmarksObj instanceof List<?> list) {
            builder.allowListBookmarks((List<Map<String, Object>>) allowBookmarksObj);
        }

        // Plugin tarafı
        builder.filterBrowsers((Boolean) map.getOrDefault("filterBrowsers", false));
        builder.filterSockets((Boolean) map.getOrDefault("filterSockets", false));
        builder.filterPackets((Boolean) map.getOrDefault("filterPackets", false));
        builder.filterURLs((Boolean) map.getOrDefault("filterURLs", false));

        builder.filterGrade(map.getOrDefault("filterGrade", "firewall").toString());

        Object pluginBundleIDObj = map.get("pluginBundleID");
        if (pluginBundleIDObj != null) {
            builder.pluginBundleID(pluginBundleIDObj.toString());
        }

        Object contentFilterUUIDObj = map.get("contentFilterUUID");
        if (contentFilterUUIDObj != null) {
            builder.contentFilterUUID(contentFilterUUIDObj.toString());
        }

        Object dataBundleIdObj = map.get("filterDataProviderBundleIdentifier");
        if (dataBundleIdObj != null) {
            builder.filterDataProviderBundleIdentifier(dataBundleIdObj.toString());
        }

        Object dataReqObj = map.get("filterDataProviderDesignatedRequirement");
        if (dataReqObj != null) {
            builder.filterDataProviderDesignatedRequirement(dataReqObj.toString());
        }

        Object packetBundleIdObj = map.get("filterPacketProviderBundleIdentifier");
        if (packetBundleIdObj != null) {
            builder.filterPacketProviderBundleIdentifier(packetBundleIdObj.toString());
        }

        Object packetReqObj = map.get("filterPacketProviderDesignatedRequirement");
        if (packetReqObj != null) {
            builder.filterPacketProviderDesignatedRequirement(packetReqObj.toString());
        }

        Object organizationObj = map.get("organization");
        if (organizationObj != null) {
            builder.organization(organizationObj.toString());
        }

        Object userNameObj = map.get("userName");
        if (userNameObj != null) {
            builder.userName(userNameObj.toString());
        }

        Object passwordObj = map.get("password");
        if (passwordObj != null) {
            builder.password(passwordObj.toString());
        }

        Object serverAddressObj = map.get("serverAddress");
        if (serverAddressObj != null) {
            builder.serverAddress(serverAddressObj.toString());
        }

        Object payloadCertUUIDObj = map.get("payloadCertificateUUID");
        if (payloadCertUUIDObj != null) {
            builder.payloadCertificateUUID(payloadCertUUIDObj.toString());
        }

        Object urlFilterParamsObj = map.get("urlFilterParameters");
        if (urlFilterParamsObj instanceof Map<?, ?> paramsMap) {
            builder.urlFilterParameters((Map<String, Object>) paramsMap);
        }

        Object vendorConfigObj = map.get("vendorConfig");
        if (vendorConfigObj instanceof Map<?, ?> vMap) {
            builder.vendorConfig((Map<String, Object>) vMap);
        }

        WebContentFilter filter = builder.build();

        // BasePayload alanları
        filter.setPayloadIdentifier(String.format("policy_webcontentfilter-%s", policyId));
        filter.setPayloadType("com.apple.webcontent-filter");
        filter.setPayloadUUID(UUID.randomUUID().toString());
        filter.setPayloadVersion(1);

        return filter;
    }

    public NSDictionary createPayload() {
        NSDictionary dict = new NSDictionary();

        // FilterType
        if (this.getFilterType() != null) {
            dict.put("FilterType", this.getFilterType());
        } else {
            dict.put("FilterType", "BuiltIn");
        }

        if ("BuiltIn".equalsIgnoreCase(this.getFilterType())) {
            // Built-in config
            dict.put("AutoFilterEnabled", Boolean.TRUE.equals(this.getAutoFilterEnabled()));

            if (this.getDenyListURLs() != null && !this.getDenyListURLs().isEmpty()) {
                dict.put("DenyListURLs", this.getDenyListURLs().toArray(new String[0]));
            }

            if (this.getPermittedURLs() != null && !this.getPermittedURLs().isEmpty()) {
                dict.put("PermittedURLs", this.getPermittedURLs().toArray(new String[0]));
            }

            if (this.getAllowListBookmarks() != null && !this.getAllowListBookmarks().isEmpty()) {
                // Basit: bookmark dict'lerini direkt koyuyoruz (UI tarafında dict map'i üretmen gerekiyor)
                var bookmarkArray = new com.dd.plist.NSArray(this.getAllowListBookmarks().size());
                for (int i = 0; i < this.getAllowListBookmarks().size(); i++) {
                    bookmarkArray.setValue(i, mapToNSDictionary(this.getAllowListBookmarks().get(i)));
                }
                dict.put("AllowListBookmarks", bookmarkArray);
            }

            dict.put("SafariHistoryRetentionEnabled", Boolean.TRUE.equals(this.getSafariHistoryRetentionEnabled()));
            dict.put("HideDenyListURLs", Boolean.TRUE.equals(this.getHideDenyListURLs()));

        } else if ("Plugin".equalsIgnoreCase(this.getFilterType())) {
            // Plugin config
            dict.put("FilterBrowsers", Boolean.TRUE.equals(this.getFilterBrowsers()));
            dict.put("FilterSockets", Boolean.TRUE.equals(this.getFilterSockets()));
            dict.put("FilterPackets", Boolean.TRUE.equals(this.getFilterPackets()));
            dict.put("FilterURLs", Boolean.TRUE.equals(this.getFilterURLs()));

            if (this.getFilterGrade() != null) {
                dict.put("FilterGrade", this.getFilterGrade());
            }

            if (this.getPluginBundleID() != null) {
                dict.put("PluginBundleID", this.getPluginBundleID());
            }

            if (this.getContentFilterUUID() != null) {
                dict.put("ContentFilterUUID", this.getContentFilterUUID());
            }

            if (this.getFilterDataProviderBundleIdentifier() != null) {
                dict.put("FilterDataProviderBundleIdentifier", this.getFilterDataProviderBundleIdentifier());
            }

            if (this.getFilterDataProviderDesignatedRequirement() != null) {
                dict.put("FilterDataProviderDesignatedRequirement", this.getFilterDataProviderDesignatedRequirement());
            }

            if (this.getFilterPacketProviderBundleIdentifier() != null) {
                dict.put("FilterPacketProviderBundleIdentifier", this.getFilterPacketProviderBundleIdentifier());
            }

            if (this.getFilterPacketProviderDesignatedRequirement() != null) {
                dict.put("FilterPacketProviderDesignatedRequirement", this.getFilterPacketProviderDesignatedRequirement());
            }

            if (this.getOrganization() != null) {
                dict.put("Organization", this.getOrganization());
            }

            if (this.getUserName() != null) {
                dict.put("UserName", this.getUserName());
            }

            if (this.getPassword() != null) {
                dict.put("Password", this.getPassword());
            }

            if (this.getServerAddress() != null) {
                dict.put("ServerAddress", this.getServerAddress());
            }

            if (this.getPayloadCertificateUUID() != null) {
                dict.put("PayloadCertificateUUID", this.getPayloadCertificateUUID());
            }

            if (this.getUserDefinedName() != null) {
                dict.put("UserDefinedName", this.getUserDefinedName());
            }

            if (this.getUrlFilterParameters() != null && !this.getUrlFilterParameters().isEmpty()) {
                dict.put("URLFilterParameters", mapToNSDictionary(this.getUrlFilterParameters()));
            }

            if (this.getVendorConfig() != null && !this.getVendorConfig().isEmpty()) {
                dict.put("VendorConfig", mapToNSDictionary(this.getVendorConfig()));
            }
        }

        // Base payload alanları
        dict.put("PayloadIdentifier", this.getPayloadIdentifier());
        dict.put("PayloadType", this.getPayloadType());
        dict.put("PayloadUUID", this.getPayloadUUID());
        dict.put("PayloadVersion", this.getPayloadVersion());

        return dict;
    }

    // Basit Map<String,Object> -> NSDictionary çevirici
    private NSDictionary mapToNSDictionary(Map<String, Object> map) {
        NSDictionary nsDict = new NSDictionary();
        if (map == null) {
            return nsDict;
        }
        map.forEach((k, v) -> {
            if (v == null) return;

            if (v instanceof Map<?, ?> innerMap) {
                nsDict.put(k, mapToNSDictionary((Map<String, Object>) innerMap));
            } else if (v instanceof List<?> list) {
                var arr = new com.dd.plist.NSArray(list.size());
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map<?, ?> m) {
                        arr.setValue(i, mapToNSDictionary((Map<String, Object>) m));
                    } else {
                        arr.setValue(i, item);
                    }
                }
                nsDict.put(k, arr);
            } else {
                nsDict.put(k, v);
            }
        });
        return nsDict;
    }
}