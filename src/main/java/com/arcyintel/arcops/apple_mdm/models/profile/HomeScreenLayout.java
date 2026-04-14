package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import lombok.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Setter @Getter @Builder @AllArgsConstructor @NoArgsConstructor
public class HomeScreenLayout extends BasePayload {
    private List<IconItem> dock;
    private List<List<IconItem>> pages;

    @Setter @Getter @Builder @AllArgsConstructor @NoArgsConstructor
    public static class IconItem {
        /** Required. Possible values: Application, Folder, WebClip */
        private String type;

        /** Required when type == Application */
        private String bundleId;

        /** Valid only when type == Folder */
        private String displayName;

        /** Required when type == WebClip */
        private String url;

        /** Valid only when type == Folder */
        private List<List<IconItem>> pages;
    }

    private static final String TYPE_APPLICATION = "Application";
    private static final String TYPE_FOLDER = "Folder";
    private static final String TYPE_WEBCLIP = "WebClip";

    @SuppressWarnings("unchecked")
    public static HomeScreenLayout createFromMap(Map<String, Object> map, UUID policyId) {
        HomeScreenLayout payload = HomeScreenLayout.builder()
                .dock(parseIconItemList((List<Object>) map.get("Dock")))
                .pages(parseIconItemPages((List<Object>) map.get("Pages")))
                .build();

        payload.setPayloadIdentifier("com.apple.homescreenlayout." + policyId);
        payload.setPayloadType("com.apple.homescreenlayout");
        payload.setPayloadUUID(UUID.randomUUID().toString());
        payload.setPayloadVersion(1);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static List<IconItem> parseIconItemList(List<Object> rawItems) {
        if (rawItems == null) return null;
        return rawItems.stream()
                .filter(o -> o instanceof Map)
                .map(o -> parseIconItem((Map<String, Object>) o))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<List<IconItem>> parseIconItemPages(List<Object> rawPages) {
        if (rawPages == null) return null;
        return rawPages.stream()
                .filter(o -> o instanceof List)
                .map(o -> parseIconItemList((List<Object>) o))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static IconItem parseIconItem(Map<String, Object> itemMap) {
        if (itemMap == null) return null;

        String type = (String) itemMap.get("Type");
        IconItem.IconItemBuilder builder = IconItem.builder().type(type);

        if (itemMap.containsKey("BundleID")) builder.bundleId((String) itemMap.get("BundleID"));
        if (itemMap.containsKey("DisplayName")) builder.displayName((String) itemMap.get("DisplayName"));
        if (itemMap.containsKey("URL")) builder.url((String) itemMap.get("URL"));

        // Folder pages (recursive)
        if (TYPE_FOLDER.equals(type) && itemMap.containsKey("Pages")) {
            Object pagesObj = itemMap.get("Pages");
            if (pagesObj instanceof List) {
                builder.pages(parseIconItemPages((List<Object>) pagesObj));
            }
        }

        return builder.build();
    }

    public NSDictionary createPayload() {
        NSDictionary d = new NSDictionary();

        NSArray dockArray = convertIconItems(dock);
        if (dockArray != null) {
            d.put("Dock", dockArray);
        }

        if (pages != null) {
            NSArray pagesArray = new NSArray(pages.size());
            for (int i = 0; i < pages.size(); i++) {
                pagesArray.setValue(i, convertIconItems(pages.get(i)));
            }
            d.put("Pages", pagesArray);
        }

        d.put("PayloadIdentifier", getPayloadIdentifier());
        d.put("PayloadType", getPayloadType());
        d.put("PayloadUUID", getPayloadUUID());
        d.put("PayloadVersion", getPayloadVersion());
        return d;
    }

    private NSArray convertIconItems(List<IconItem> items) {
        if (items == null) return null;

        NSArray array = new NSArray(items.size());
        for (int i = 0; i < items.size(); i++) {
            IconItem item = items.get(i);
            NSDictionary itemDict = new NSDictionary();

            itemDict.put("Type", item.getType());

            if (item.getBundleId() != null) itemDict.put("BundleID", item.getBundleId());
            if (item.getDisplayName() != null) itemDict.put("DisplayName", item.getDisplayName());
            if (item.getUrl() != null) itemDict.put("URL", item.getUrl());

            if (TYPE_FOLDER.equals(item.getType()) && item.getPages() != null) {
                NSArray folderPagesArray = new NSArray(item.getPages().size());
                for (int j = 0; j < item.getPages().size(); j++) {
                    folderPagesArray.setValue(j, convertIconItems(item.getPages().get(j)));
                }
                itemDict.put("Pages", folderPagesArray);
            }

            array.setValue(i, itemDict);
        }
        return array;
    }
}