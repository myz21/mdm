package com.arcyintel.arcops.apple_mdm.services.app;

import com.arcyintel.arcops.apple_mdm.domains.EnterpriseApp;
import com.arcyintel.arcops.apple_mdm.domains.ItunesAppMeta;
import com.arcyintel.arcops.apple_mdm.models.api.appresolve.AppResolveRequest.AppResolveRequestItem;
import com.arcyintel.arcops.apple_mdm.models.api.appresolve.AppResolveResponseItem;
import com.arcyintel.arcops.apple_mdm.repositories.EnterpriseAppRepository;
import com.arcyintel.arcops.apple_mdm.repositories.ItunesAppMetaRepository;
import com.arcyintel.arcops.apple_mdm.services.app.AppResolveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppResolveServiceImpl implements AppResolveService {

    private final ItunesAppMetaRepository itunesAppMetaRepository;
    private final EnterpriseAppRepository enterpriseAppRepository;

    /**
     * Static icon map for Apple built-in system apps.
     * These apps are pre-installed on every iOS/macOS device and their icons
     * are hosted on Apple's mzstatic CDN (stable, public URLs).
     * Covers ~40 well-known system apps.
     */
    private static final Map<String, SystemAppInfo> APPLE_SYSTEM_APPS = Map.ofEntries(
            Map.entry("com.apple.mobilesafari", new SystemAppInfo("Safari",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/23/4c/cb/234ccbb4-e65a-bb94-f877-3d230743e9e3/safari-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.mobiletimer", new SystemAppInfo("Clock",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/4c/f8/90/4cf89006-bf3a-4757-0061-cdc8d79373f6/clock-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.calculator", new SystemAppInfo("Calculator",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/0f/2a/0b/0f2a0b1f-77ff-9e66-dfbe-d9b92d3eae16/calculator-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.camera", new SystemAppInfo("Camera",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/ae/c8/c2/aec8c2c0-4676-db2d-4ed1-e7d0ce768a15/camera-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.weather", new SystemAppInfo("Weather",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/0e/7e/ee/0e7eeec9-8436-eb79-99a5-f2e92803914d/weather-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.Maps", new SystemAppInfo("Maps",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/4a/40/ea/4a40ea55-14c6-81a4-1ddd-aa2f8481f518/maps-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.Health", new SystemAppInfo("Health",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/1a/5a/1f/1a5a1ffd-7db8-ba85-10ad-93c11b085683/health-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.Translate", new SystemAppInfo("Translate",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/f0/67/8f/f0678f02-ba29-a7e8-330a-976638e6f939/translate-0-0-1x_U007epad-0-1-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.measure", new SystemAppInfo("Measure",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/ff/f5/09/fff509ef-0c5a-8678-1338-dceecea14fe5/measure-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.mobilenotes", new SystemAppInfo("Notes",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/96/85/52/96855209-27d3-bfb0-0516-105a2223ef50/notes-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.mobilecal", new SystemAppInfo("Calendar",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/58/79/e0/5879e09e-20ac-a4c1-80e1-5669247cad4c/calendar-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.mobilemail", new SystemAppInfo("Mail",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/cf/a0/8c/cfa08c8a-5789-d605-a3cb-760ab70d52e1/mail-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.mobileslideshow", new SystemAppInfo("Photos",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/a4/c2/8e/a4c28eea-f904-db56-ff8f-8d51dd9c0d6e/photos-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.MobileSMS", new SystemAppInfo("Messages",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/0e/08/07/0e080793-1b66-d9b3-0bbe-8222669abf79/messages-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.findmy", new SystemAppInfo("Find My",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/e7/f8/dc/e7f8dca5-9326-58ce-a058-74269c035717/findmy-0-0-1x_U007epad-0-1-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.Music", new SystemAppInfo("Music",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/06/1c/45/061c4521-3735-aee2-87aa-8077d591d25c/music-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.podcasts", new SystemAppInfo("Podcasts",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/7c/51/e2/7c51e245-9fdd-109c-1f91-245d71c20608/podcasts-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.facetime", new SystemAppInfo("FaceTime",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/9a/11/89/9a11899c-f0fb-c22b-2c41-a3c2540abf5f/facetime-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.shortcuts", new SystemAppInfo("Shortcuts",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/7a/0c/b4/7a0cb40c-4ed2-4d65-fd2f-45b3584cf3d2/shortcuts-0-0-1x_U007epad-0-1-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.freeform", new SystemAppInfo("Freeform",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/c4/fe/02/c4fe02fe-b3ab-01ad-655e-723c4f3d2229/freeform-0-0-1x_U007epad-0-1-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.iBooks", new SystemAppInfo("Books",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/fc/29/87/fc29870e-93f7-1c92-9805-c21f1679f245/books-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.news", new SystemAppInfo("News",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/61/4f/03/614f03bf-3926-a358-4cfc-09456dd55631/news-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.stocks", new SystemAppInfo("Stocks",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/c8/55/72/c85572c9-c5fe-468b-1b89-6f6cf11a2007/stocks-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.tips", new SystemAppInfo("Tips",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/c2/82/bc/c282bc4d-7d60-1c41-3282-1d9accecf0bd/tips-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.tv", new SystemAppInfo("TV",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/6b/0d/39/6b0d3998-f722-2063-e20e-a3ba4ee6e694/tv-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.Home", new SystemAppInfo("Home",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/76/51/29/76512900-4cc1-8c90-ecf5-d45451ebe35f/home-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.reminders", new SystemAppInfo("Reminders",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/47/ff/b4/47ffb497-6d42-0628-c5a3-fad0de052421/reminders-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.Passbook", new SystemAppInfo("Wallet",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/8b/dc/f9/8bdcf93b-2d9e-e45f-f2a1-2461ebad923e/AppIcon-0-0-1x_U007ephone-0-1-P3-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.compass", new SystemAppInfo("Compass",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/a5/e2/17/a5e217cd-4116-ebbe-3303-24a1ccb9439d/compass-0-0-1x_U007ephone-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.DocumentsApp", new SystemAppInfo("Files",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/cf/a1/e7/cfa1e733-abf2-2a89-394f-1de4a5043743/files-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.VoiceMemos", new SystemAppInfo("Voice Memos",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/52/24/32/52243281-b115-79d1-bab3-3cb135bcc69f/voicememos-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.AppStore", new SystemAppInfo("App Store",
                    null)), // Not available on iTunes Store
            Map.entry("com.apple.Magnifier", new SystemAppInfo("Magnifier",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/ae/e1/a2/aee1a2c5-429f-672b-77b4-21a25710d0f5/magnifier-0-0-1x_U007epad-0-1-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.MobileStore", new SystemAppInfo("iTunes Store",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/9e/01/e8/9e01e8fe-1246-0f06-e075-46b3f5f01f7d/itunesstore-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.journal", new SystemAppInfo("Journal",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/dc/8d/af/dc8daf9c-9da1-03f3-c341-c6c3eccd817e/journal-0-0-1x_U007epad-0-1-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.Passwords", new SystemAppInfo("Passwords",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/3f/c1/0f/3fc10f91-a212-6835-e7bf-342c6102d5c9/passwords-0-0-1x_U007epad-0-1-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.Fitness", new SystemAppInfo("Fitness",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/87/f2/57/87f257db-8764-cc53-b935-2a8ab310c9e0/fitness-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.MobileAddressBook", new SystemAppInfo("Contacts",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/c3/e0/9d/c3e09d8c-fb97-f0be-8463-b011f602c53a/contacts-0-0-1x_U007epad-0-1-0-sRGB-0-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.Bridge", new SystemAppInfo("Watch",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/a4/23/32/a42332c6-4adb-f12a-b72f-9680742f3418/watch.companion-0-0-1x_U007ephone-0-1-0-sRGB-85-220.png/100x100bb.jpg")),
            Map.entry("com.apple.games", new SystemAppInfo("Games",
                    null)), // Not available on iTunes Store
            Map.entry("com.apple.Preview", new SystemAppInfo("Preview",
                    "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/5b/15/a7/5b15a7ef-7ad9-4ccc-bbd6-2b8fb40a4fbf/preview-0-0-1x_U007epad-0-1-sRGB-85-220.png/100x100bb.jpg"))
    );

    private record SystemAppInfo(String name, String iconUrl) {}

    @Override
    public List<AppResolveResponseItem> resolveApps(List<AppResolveRequestItem> apps) {
        if (apps == null || apps.isEmpty()) {
            return List.of();
        }

        // Partition by type
        List<String> vppBundleIds = apps.stream()
                .filter(a -> "vpp".equalsIgnoreCase(a.getType()))
                .map(AppResolveRequestItem::getBundleId)
                .distinct()
                .toList();

        List<String> enterpriseBundleIds = apps.stream()
                .filter(a -> "enterprise".equalsIgnoreCase(a.getType()))
                .map(AppResolveRequestItem::getBundleId)
                .distinct()
                .toList();

        // Batch fetch from local DB
        Map<String, ItunesAppMeta> vppMap = vppBundleIds.isEmpty()
                ? Map.of()
                : itunesAppMetaRepository.findAllByBundleIdIn(vppBundleIds).stream()
                        .collect(Collectors.toMap(ItunesAppMeta::getBundleId, a -> a, (a, b) -> a));

        Map<String, EnterpriseApp> enterpriseMap = enterpriseBundleIds.isEmpty()
                ? Map.of()
                : enterpriseAppRepository.findAllByBundleIdIn(enterpriseBundleIds).stream()
                        .collect(Collectors.toMap(EnterpriseApp::getBundleId, a -> a, (a, b) -> a));

        // Build response preserving request order
        List<AppResolveResponseItem> results = new ArrayList<>();
        for (AppResolveRequestItem req : apps) {
            var builder = AppResolveResponseItem.builder()
                    .bundleId(req.getBundleId())
                    .type(req.getType());

            if ("vpp".equalsIgnoreCase(req.getType())) {
                // 1. Check local VPP database
                ItunesAppMeta meta = vppMap.get(req.getBundleId());
                if (meta != null) {
                    String iconUrl = meta.getArtworkUrl100() != null
                            ? meta.getArtworkUrl100()
                            : meta.getArtworkUrl60();
                    builder.name(meta.getTrackName()).iconUrl(iconUrl).found(true);
                } else {
                    // 2. Fallback: static Apple system app icon map
                    SystemAppInfo systemApp = APPLE_SYSTEM_APPS.get(req.getBundleId());
                    if (systemApp != null && systemApp.iconUrl() != null) {
                        builder.name(systemApp.name()).iconUrl(systemApp.iconUrl()).found(true);
                    } else if (systemApp != null) {
                        // Known app but no icon URL (e.g. App Store)
                        builder.name(systemApp.name()).found(true);
                    } else {
                        builder.name(req.getBundleId()).found(false);
                    }
                }
            } else if ("enterprise".equalsIgnoreCase(req.getType())) {
                EnterpriseApp ea = enterpriseMap.get(req.getBundleId());
                if (ea != null) {
                    String iconUrl = ea.getIconBase64() != null && !ea.getIconBase64().isBlank()
                            ? "data:image/png;base64," + ea.getIconBase64()
                            : null;
                    builder.name(ea.getDisplayName()).iconUrl(iconUrl).found(true);
                } else {
                    builder.name(req.getBundleId()).found(false);
                }
            } else {
                builder.name(req.getBundleId()).found(false);
            }

            results.add(builder.build());
        }

        return results;
    }
}
