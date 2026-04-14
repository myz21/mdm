package com.arcyintel.arcops.apple_mdm.services.app;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListParser;
import com.arcyintel.arcops.apple_mdm.domains.EnterpriseApp;
import com.arcyintel.arcops.apple_mdm.models.api.enterpriseapp.*;
import com.arcyintel.arcops.apple_mdm.repositories.EnterpriseAppRepository;
import com.arcyintel.arcops.apple_mdm.services.app.EnterpriseAppService;
import com.arcyintel.arcops.apple_mdm.services.mappers.EnterpriseAppMapper;
import com.arcyintel.arcops.apple_mdm.specifications.EnterpriseAppFilterSpecification;
import com.arcyintel.arcops.commons.api.DynamicListRequestDto;
import com.arcyintel.arcops.commons.exceptions.BusinessException;
import com.arcyintel.arcops.commons.exceptions.ConflictException;
import com.arcyintel.arcops.commons.exceptions.EntityNotFoundException;
import com.arcyintel.arcops.commons.utils.FieldFilterUtil;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;

@Service
@RequiredArgsConstructor
public class EnterpriseAppServiceImpl implements EnterpriseAppService {

    private static final Logger logger = LoggerFactory.getLogger(EnterpriseAppServiceImpl.class);
    private static final Path STORAGE_ROOT = Paths.get("apps");
    private static final String ENTERPRISE_SUB_DIR = "enterprise";

    private final EnterpriseAppRepository enterpriseAppRepository;
    private final EnterpriseAppMapper enterpriseAppMapper;
    private final TempAppStorage tempAppStorage;

    @org.springframework.beans.factory.annotation.Value("${host:https://localhost:8085/api/apple}")
    private String serverHost;

    // ── Upload ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public GetEnterpriseAppDto upload(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "File name is required");
        }

        String lowerName = originalFilename.toLowerCase();
        if (!lowerName.endsWith(".ipa") && !lowerName.endsWith(".pkg")) {
            throw new BusinessException("VALIDATION_ERROR",
                    "Only .ipa and .pkg files are accepted. Got: " + originalFilename);
        }

        boolean isIpa = lowerName.endsWith(".ipa");
        String platform = isIpa ? "ios" : "macos";

        logger.info("Uploading enterprise app: filename={}, platform={}, size={}", originalFilename, platform, file.getSize());

        try {
            // Save to temp file first to avoid reading the stream multiple times
            Path tempFile = Files.createTempFile("enterprise-upload-", originalFilename);
            try {
                file.transferTo(tempFile.toFile());

                // Parse metadata from the binary (pass original filename for fallback)
                EnterpriseApp.EnterpriseAppBuilder builder = isIpa
                        ? parseIpa(tempFile)
                        : parsePkg(tempFile, originalFilename);

                builder.platform(platform);
                builder.fileName(originalFilename);
                builder.fileSizeBytes(Files.size(tempFile));

                // Compute SHA-256
                builder.fileHash(computeSha256(tempFile));

                // Build entity (without storage path yet, need ID from DB)
                EnterpriseApp app = builder.build();

                // Validate parsed metadata
                if (app.getBundleId() == null || app.getBundleId().isBlank()) {
                    throw new BusinessException("VALIDATION_ERROR",
                            "Could not extract bundle ID from the uploaded file");
                }

                // Check for duplicates
                Optional<EnterpriseApp> existing = enterpriseAppRepository
                        .findByBundleIdAndVersion(app.getBundleId(), app.getVersion());
                if (existing.isPresent()) {
                    throw new ConflictException(
                            "Enterprise app already exists: bundleId=" + app.getBundleId()
                                    + ", version=" + app.getVersion());
                }

                // Save entity first to get generated ID
                app.setStoragePath("temp"); // temporary, will update after we know the ID
                EnterpriseApp saved = enterpriseAppRepository.saveAndFlush(app);

                // Now create storage path using the generated ID
                String subDir = ENTERPRISE_SUB_DIR + "/" + saved.getId();
                Path destDir = STORAGE_ROOT.resolve(subDir);
                Files.createDirectories(destDir);

                Path destFile = destDir.resolve(Paths.get(originalFilename)).normalize().toAbsolutePath();
                if (!destFile.startsWith(STORAGE_ROOT.toAbsolutePath())) {
                    throw new BusinessException("VALIDATION_ERROR",
                            "Invalid file name — path traversal detected");
                }

                Files.copy(tempFile, destFile, StandardCopyOption.REPLACE_EXISTING);

                // Update storage path
                saved.setStoragePath(subDir + "/" + originalFilename);
                saved = enterpriseAppRepository.save(saved);

                logger.info("Enterprise app uploaded. id={}, bundleId={}, version={}", saved.getId(),
                        saved.getBundleId(), saved.getVersion());

                return enterpriseAppMapper.toDto(saved);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to upload enterprise app: {}", e.getMessage(), e);
            throw new BusinessException("UPLOAD_ERROR",
                    "Failed to process uploaded file: " + e.getMessage(), e);
        }
    }

    // ── Phase 1: Upload + Analyze ─────────────────────────────────────────

    @Override
    public AppAnalyzeResponse uploadAndAnalyze(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "File name is required");
        }

        String lowerName = originalFilename.toLowerCase();
        if (!lowerName.endsWith(".ipa") && !lowerName.endsWith(".pkg")) {
            throw new BusinessException("VALIDATION_ERROR",
                    "Only .ipa and .pkg files are accepted. Got: " + originalFilename);
        }

        boolean isIpa = lowerName.endsWith(".ipa");
        String platform = isIpa ? "ios" : "macos";

        logger.info("Phase 1 — Upload and analyze: filename={}, platform={}, size={}",
                originalFilename, platform, file.getSize());

        try {
            String tempId = tempAppStorage.store(file);
            Path tempFile = tempAppStorage.getFile(tempId);

            // Parse metadata
            AppMetadata metadata;
            try {
                EnterpriseApp.EnterpriseAppBuilder builder = isIpa
                        ? parseIpa(tempFile)
                        : parsePkg(tempFile, originalFilename);
                builder.platform(platform);
                EnterpriseApp parsed = builder.build();

                metadata = AppMetadata.builder()
                        .bundleId(parsed.getBundleId())
                        .version(parsed.getVersion())
                        .buildVersion(parsed.getBuildVersion())
                        .displayName(parsed.getDisplayName())
                        .minimumOsVersion(parsed.getMinimumOsVersion())
                        .platform(platform)
                        .supportedPlatforms(parsed.getSupportedPlatforms())
                        .iconBase64(parsed.getIconBase64())
                        .build();
            } catch (Exception e) {
                logger.warn("App parsing failed, returning empty metadata: {}", e.getMessage());
                String baseName = originalFilename.replaceAll("\\.[^.]+$", "");
                metadata = AppMetadata.builder()
                        .bundleId("")
                        .version("")
                        .displayName(baseName)
                        .platform(platform)
                        .supportedPlatforms(isIpa ? List.of("iPhone", "iPad") : List.of("Mac"))
                        .build();
            }

            String fileHash = computeSha256(tempFile);

            return AppAnalyzeResponse.builder()
                    .tempId(tempId)
                    .fileName(originalFilename)
                    .fileSizeBytes(Files.size(tempFile))
                    .fileHash(fileHash)
                    .metadata(metadata)
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to upload and analyze app: {}", e.getMessage(), e);
            throw new BusinessException("UPLOAD_ERROR",
                    "Failed to process uploaded file: " + e.getMessage(), e);
        }
    }

    // ── Phase 2: Confirm Upload ─────────────────────────────────────────────

    @Override
    @Transactional
    public GetEnterpriseAppDto confirmUpload(String tempId, ConfirmUploadRequest request) {
        logger.info("Phase 2 — Confirming upload: tempId={}", tempId);

        try {
            Path tempFile = tempAppStorage.getFile(tempId);
            String originalFilename = tempAppStorage.getFilename(tempId);

            String bundleId = request.getBundleId();
            if (bundleId == null || bundleId.isBlank()) {
                throw new BusinessException("VALIDATION_ERROR", "Bundle ID is required");
            }

            // Check for duplicates
            Optional<EnterpriseApp> existing = enterpriseAppRepository
                    .findByBundleIdAndVersion(bundleId, request.getVersion());
            if (existing.isPresent()) {
                throw new ConflictException(
                        "Enterprise app already exists: bundleId=" + bundleId
                                + ", version=" + request.getVersion());
            }

            String lowerName = originalFilename.toLowerCase();
            boolean isIpa = lowerName.endsWith(".ipa");
            String platform = isIpa ? "ios" : "macos";

            String fileHash = computeSha256(tempFile);

            // Re-extract icon from temp file (don't rely on frontend sending it back)
            String iconBase64 = null;
            if (isIpa) {
                try {
                    EnterpriseApp.EnterpriseAppBuilder iconBuilder = parseIpa(tempFile);
                    EnterpriseApp parsed = iconBuilder.build();
                    iconBase64 = parsed.getIconBase64();
                } catch (Exception e) {
                    logger.warn("Failed to extract icon during confirm: {}", e.getMessage());
                }
            }

            EnterpriseApp app = EnterpriseApp.builder()
                    .bundleId(bundleId)
                    .version(request.getVersion())
                    .buildVersion(request.getBuildVersion())
                    .displayName(request.getDisplayName() != null && !request.getDisplayName().isBlank()
                            ? request.getDisplayName() : bundleId)
                    .minimumOsVersion(request.getMinimumOsVersion())
                    .platform(platform)
                    .supportedPlatforms(request.getSupportedPlatforms() != null
                            ? new ArrayList<>(request.getSupportedPlatforms())
                            : (isIpa ? new ArrayList<>(List.of("iPhone", "iPad")) : new ArrayList<>(List.of("Mac"))))
                    .fileSizeBytes(Files.size(tempFile))
                    .fileName(originalFilename)
                    .storagePath("temp")
                    .fileHash(fileHash)
                    .iconBase64(iconBase64)
                    .build();

            EnterpriseApp saved = enterpriseAppRepository.saveAndFlush(app);

            // Move to permanent storage
            String subDir = ENTERPRISE_SUB_DIR + "/" + saved.getId();
            Path destDir = STORAGE_ROOT.resolve(subDir);
            Files.createDirectories(destDir);

            Path destFile = destDir.resolve(Paths.get(originalFilename).getFileName()).normalize().toAbsolutePath();
            if (!destFile.startsWith(STORAGE_ROOT.toAbsolutePath())) {
                throw new BusinessException("VALIDATION_ERROR",
                        "Invalid file name — path traversal detected");
            }

            Files.copy(tempFile, destFile, StandardCopyOption.REPLACE_EXISTING);

            saved.setStoragePath(subDir + "/" + originalFilename);
            saved = enterpriseAppRepository.save(saved);

            tempAppStorage.delete(tempId);

            logger.info("Enterprise app confirmed. id={}, bundleId={}, version={}",
                    saved.getId(), saved.getBundleId(), saved.getVersion());

            return enterpriseAppMapper.toDto(saved);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to confirm upload: {}", e.getMessage(), e);
            throw new BusinessException("UPLOAD_ERROR",
                    "Failed to confirm uploaded file: " + e.getMessage(), e);
        }
    }

    // ── Cancel Upload ────────────────────────────────────────────────────────

    @Override
    public void cancelUpload(String tempId) {
        logger.info("Cancelling upload: tempId={}", tempId);
        tempAppStorage.delete(tempId);
    }

    // ── Get by ID ───────────────────────────────────────────────────────────

    @Override
    public GetEnterpriseAppDto getById(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "App ID is required");
        }
        EnterpriseApp app = enterpriseAppRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new EntityNotFoundException("Enterprise app not found: " + id));
        return enterpriseAppMapper.toDto(app);
    }

    // ── Get by Bundle ID ────────────────────────────────────────────────────

    @Override
    public GetEnterpriseAppDto getByBundleId(String bundleId) {
        if (bundleId == null || bundleId.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Bundle ID is required");
        }
        EnterpriseApp app = enterpriseAppRepository.findByBundleId(bundleId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Enterprise app not found with bundleId: " + bundleId));
        return enterpriseAppMapper.toDto(app);
    }

    // ── List ────────────────────────────────────────────────────────────────

    @Override
    public PagedModel<Map<String, Object>> list(DynamicListRequestDto request) {
        if (request.getPage() < 0 || request.getSize() <= 0) {
            throw new BusinessException("VALIDATION_ERROR", "Invalid pagination parameters");
        }

        EnterpriseAppFilterSpecification spec = new EnterpriseAppFilterSpecification(
                request.getFilters(), request.isFuzzy(), request.getFuzzyThreshold());

        Page<EnterpriseApp> apps = enterpriseAppRepository.findAll(spec,
                PageRequest.of(request.getPage(), request.getSize()));

        if (apps.isEmpty()) {
            return new PagedModel<>(Page.empty());
        }

        Set<String> fields = request.getFields();
        Page<Map<String, Object>> result = apps.map(app -> {
            GetEnterpriseAppDto dto = enterpriseAppMapper.toDto(app);
            return FieldFilterUtil.filterFields(dto, fields);
        });

        logger.info("Retrieved {} enterprise apps with {} fields.", result.getTotalElements(),
                fields == null || fields.isEmpty() ? "all" : fields.size());
        return new PagedModel<>(result);
    }

    // ── Download ────────────────────────────────────────────────────────────

    @Override
    public Resource download(UUID id) {
        EnterpriseApp app = enterpriseAppRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Enterprise app not found: " + id));

        Path filePath = STORAGE_ROOT.resolve(app.getStoragePath()).normalize().toAbsolutePath();
        if (!filePath.startsWith(STORAGE_ROOT.toAbsolutePath())) {
            throw new BusinessException("STORAGE_ERROR", "Invalid storage path");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new EntityNotFoundException(
                        "File not found on disk for enterprise app: " + id);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new BusinessException("STORAGE_ERROR",
                    "Could not read file: " + e.getMessage(), e);
        }
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void delete(UUID id) {
        EnterpriseApp app = enterpriseAppRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Enterprise app not found: " + id));

        // Delete physical file
        try {
            Path filePath = STORAGE_ROOT.resolve(app.getStoragePath());
            Files.deleteIfExists(filePath);

            // Try to delete the parent UUID directory if empty
            Path parentDir = filePath.getParent();
            if (parentDir != null && Files.isDirectory(parentDir)) {
                try (var entries = Files.list(parentDir)) {
                    if (entries.findFirst().isEmpty()) {
                        Files.deleteIfExists(parentDir);
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Could not delete file for enterprise app {}: {}", id, e.getMessage());
        }

        enterpriseAppRepository.deleteById(id);
        logger.info("Enterprise app deleted. id={}", id);
    }

    // ── Manifest Generation ────────────────────────────────────────────────

    @Override
    public String getManifest(UUID id) {
        EnterpriseApp app = enterpriseAppRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Enterprise app not found: " + id));

        String downloadUrl = serverHost + "/enterprise-apps/" + id + "/download";

        // Build Apple manifest plist for enterprise app installation
        // https://developer.apple.com/documentation/devicemanagement/installapplicationcommand/command/manifest
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n");
        sb.append("<plist version=\"1.0\">\n");
        sb.append("<dict>\n");
        sb.append("    <key>items</key>\n");
        sb.append("    <array>\n");
        sb.append("        <dict>\n");
        sb.append("            <key>assets</key>\n");
        sb.append("            <array>\n");
        sb.append("                <dict>\n");
        sb.append("                    <key>kind</key>\n");
        sb.append("                    <string>software-package</string>\n");
        sb.append("                    <key>url</key>\n");
        sb.append("                    <string>").append(escapeXml(downloadUrl)).append("</string>\n");
        if (app.getFileHash() != null && !app.getFileHash().isBlank()) {
            // MD5 hash for integrity check (Apple expects md5-size format)
            sb.append("                    <key>md5-size</key>\n");
            sb.append("                    <integer>").append(app.getFileSizeBytes() != null ? app.getFileSizeBytes() : 0).append("</integer>\n");
        }
        sb.append("                </dict>\n");
        sb.append("            </array>\n");
        sb.append("            <key>metadata</key>\n");
        sb.append("            <dict>\n");
        sb.append("                <key>bundle-identifier</key>\n");
        sb.append("                <string>").append(escapeXml(app.getBundleId())).append("</string>\n");
        if (app.getVersion() != null && !app.getVersion().isBlank()) {
            sb.append("                <key>bundle-version</key>\n");
            sb.append("                <string>").append(escapeXml(app.getVersion())).append("</string>\n");
        }
        sb.append("                <key>kind</key>\n");
        sb.append("                <string>software</string>\n");
        sb.append("                <key>title</key>\n");
        sb.append("                <string>").append(escapeXml(app.getDisplayName() != null ? app.getDisplayName() : app.getBundleId())).append("</string>\n");
        if (app.getFileSizeBytes() != null) {
            sb.append("                <key>sizeInBytes</key>\n");
            sb.append("                <integer>").append(app.getFileSizeBytes()).append("</integer>\n");
        }
        sb.append("            </dict>\n");
        sb.append("        </dict>\n");
        sb.append("    </array>\n");
        sb.append("</dict>\n");
        sb.append("</plist>\n");

        return sb.toString();
    }

    @Override
    public String getManifestUrl(UUID id) {
        // Verify app exists
        if (!enterpriseAppRepository.existsById(id)) {
            throw new EntityNotFoundException("Enterprise app not found: " + id);
        }
        return serverHost + "/enterprise-apps/" + id + "/manifest";
    }

    @Override
    public GetEnterpriseAppDto findByBundleIdOrNull(String bundleId) {
        if (bundleId == null || bundleId.isBlank()) {
            return null;
        }
        return enterpriseAppRepository.findByBundleId(bundleId)
                .map(enterpriseAppMapper::toDto)
                .orElse(null);
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // ── IPA Parsing ─────────────────────────────────────────────────────────

    private EnterpriseApp.EnterpriseAppBuilder parseIpa(Path ipaFile) throws Exception {
        EnterpriseApp.EnterpriseAppBuilder builder = EnterpriseApp.builder();
        boolean foundPlist = false;

        // Use ZipFile for random access (need both Info.plist and icon)
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(ipaFile.toFile())) {
            // ── Pass 1: Find and parse Info.plist ────────────────────────
            for (var entries = zipFile.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.matches("Payload/[^/]+\\.app/Info\\.plist")) {
                    byte[] plistBytes = zipFile.getInputStream(entry).readAllBytes();
                    NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(plistBytes);

                    builder.bundleId(plistString(rootDict, "CFBundleIdentifier"));
                    builder.version(plistString(rootDict, "CFBundleShortVersionString"));
                    builder.buildVersion(plistString(rootDict, "CFBundleVersion"));

                    String displayName = plistString(rootDict, "CFBundleDisplayName");
                    if (displayName == null || displayName.isBlank()) {
                        displayName = plistString(rootDict, "CFBundleName");
                    }
                    builder.displayName(displayName);
                    builder.minimumOsVersion(plistString(rootDict, "MinimumOSVersion"));

                    // Derive supported platforms from DTPlatformName
                    String dtPlatform = plistString(rootDict, "DTPlatformName");
                    List<String> platforms = new ArrayList<>();
                    if (dtPlatform != null) {
                        String lower = dtPlatform.toLowerCase();
                        if (lower.contains("iphoneos") || lower.contains("ios")) {
                            platforms.add("iPhone");
                            platforms.add("iPad");
                        } else if (lower.contains("macos") || lower.contains("macosx")) {
                            platforms.add("Mac");
                        } else if (lower.contains("tvos") || lower.contains("appletv")) {
                            platforms.add("AppleTV");
                        }
                    }
                    if (platforms.isEmpty()) {
                        platforms.add("iPhone");
                        platforms.add("iPad");
                    }
                    builder.supportedPlatforms(new ArrayList<>(platforms));

                    foundPlist = true;
                    logger.debug("Parsed IPA Info.plist: bundleId={}, version={}",
                            plistString(rootDict, "CFBundleIdentifier"),
                            plistString(rootDict, "CFBundleShortVersionString"));
                    break;
                }
            }

            // ── Pass 2: Extract largest app icon ─────────────────────────
            try {
                ZipEntry bestIcon = null;
                long bestSize = 0;

                for (var entries = zipFile.entries(); entries.hasMoreElements(); ) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();

                    // Match: Payload/X.app/AppIcon*.png or Icon*.png (not inside subdirs)
                    if (name.matches("Payload/[^/]+\\.app/(AppIcon[^/]*\\.png|Icon[^/]*\\.png)")) {
                        long size = entry.getSize() > 0 ? entry.getSize() : entry.getCompressedSize();
                        if (size > bestSize) {
                            bestSize = size;
                            bestIcon = entry;
                        }
                    }
                }

                if (bestIcon != null) {
                    byte[] iconBytes = zipFile.getInputStream(bestIcon).readAllBytes();
                    if (iconBytes.length > 0) {
                        builder.iconBase64(Base64.getEncoder().encodeToString(iconBytes));
                        logger.debug("Extracted IPA icon: {} ({} bytes)", bestIcon.getName(), iconBytes.length);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to extract icon from IPA: {}", e.getMessage());
                // Icon extraction is optional
            }
        }

        if (!foundPlist) {
            throw new BusinessException("VALIDATION_ERROR",
                    "Invalid IPA file: could not find Info.plist in Payload/*.app/");
        }

        return builder;
    }

    // ── PKG Parsing (xar format) ───────────────────────────────────────────

    private static final int XAR_MAGIC = 0x78617221; // "xar!"

    private EnterpriseApp.EnterpriseAppBuilder parsePkg(Path pkgFile, String originalFilename) throws Exception {
        EnterpriseApp.EnterpriseAppBuilder builder = EnterpriseApp.builder();
        builder.supportedPlatforms(new ArrayList<>(List.of("Mac")));

        boolean foundMetadata = false;

        try (RandomAccessFile raf = new RandomAccessFile(pkgFile.toFile(), "r")) {
            // ── Read xar header ─────────────────────────────────────────
            int magic = raf.readInt();
            if (magic != XAR_MAGIC) {
                logger.warn("PKG file does not have xar magic header");
                return fallbackFromFilename(originalFilename, builder);
            }

            short headerSize = raf.readShort();
            /* version */ raf.readShort();
            long tocCompressedLen = raf.readLong();
            long tocUncompressedLen = raf.readLong();
            /* checksum algo */ raf.readInt();

            // Skip any extra header bytes
            if (headerSize > 28) {
                raf.skipBytes(headerSize - 28);
            }

            // ── Read and inflate the TOC XML ────────────────────────────
            byte[] compressedToc = new byte[(int) tocCompressedLen];
            raf.readFully(compressedToc);

            byte[] tocBytes;
            try {
                Inflater inflater = new Inflater();
                inflater.setInput(compressedToc);
                tocBytes = new byte[(int) tocUncompressedLen];
                inflater.inflate(tocBytes);
                inflater.end();
            } catch (Exception e) {
                logger.warn("Failed to inflate xar TOC: {}", e.getMessage());
                return fallbackFromFilename(originalFilename, builder);
            }

            logger.debug("Parsed xar TOC ({} bytes)", tocBytes.length);

            // ── Parse the TOC XML to find Distribution / PackageInfo ────
            long heapStart = headerSize + tocCompressedLen;

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document toc = docBuilder.parse(new ByteArrayInputStream(tocBytes));

            // Collect all <file> elements from the TOC (recursive search)
            NodeList fileNodes = toc.getElementsByTagName("file");
            logger.debug("Found {} file entries in xar TOC", fileNodes.getLength());

            // First pass: look for Distribution at root level
            for (int i = 0; i < fileNodes.getLength() && !foundMetadata; i++) {
                Element fileEl = (Element) fileNodes.item(i);
                String name = getDirectChildText(fileEl, "name");
                if (name == null) continue;

                if ("Distribution".equalsIgnoreCase(name)) {
                    logger.debug("Found Distribution file in xar");
                    byte[] fileContent = readXarFileEntry(raf, fileEl, heapStart);
                    if (fileContent != null && parseDistributionXml(fileContent, builder)) {
                        foundMetadata = true;
                    }
                }
            }

            // Second pass: look for PackageInfo (may be in subdirectories like *.pkg/PackageInfo)
            for (int i = 0; i < fileNodes.getLength() && !foundMetadata; i++) {
                Element fileEl = (Element) fileNodes.item(i);
                String name = getDirectChildText(fileEl, "name");
                if (name == null) continue;

                if ("PackageInfo".equalsIgnoreCase(name)) {
                    logger.debug("Found PackageInfo file in xar");
                    byte[] fileContent = readXarFileEntry(raf, fileEl, heapStart);
                    if (fileContent != null && parsePackageInfoXml(fileContent, builder)) {
                        foundMetadata = true;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not parse PKG as xar archive: {}. Trying alternative parsing.", e.getMessage());
        }

        if (!foundMetadata) {
            return fallbackFromFilename(originalFilename, builder);
        }

        return builder;
    }

    /**
     * Read a file entry from the xar heap using offset/length/encoding from the TOC.
     */
    private byte[] readXarFileEntry(RandomAccessFile raf, Element fileEl, long heapStart) {
        try {
            Element dataEl = getDirectChildElement(fileEl, "data");
            if (dataEl == null) {
                logger.debug("No <data> element found for file entry");
                return null;
            }

            String offsetStr = getDirectChildText(dataEl, "offset");
            String lengthStr = getDirectChildText(dataEl, "length");
            if (offsetStr == null || lengthStr == null) {
                logger.debug("Missing offset or length in <data> element");
                return null;
            }

            long offset = Long.parseLong(offsetStr);
            long length = Long.parseLong(lengthStr);

            // Get encoding style from <encoding style="..."/>
            Element encodingEl = getDirectChildElement(dataEl, "encoding");
            String encodingStyle = encodingEl != null ? encodingEl.getAttribute("style") : "";

            logger.debug("Reading xar entry: offset={}, length={}, encoding={}", offset, length, encodingStyle);

            raf.seek(heapStart + offset);
            byte[] rawData = new byte[(int) length];
            raf.readFully(rawData);

            // Decompress based on encoding style
            if (encodingStyle.contains("gzip") || encodingStyle.contains("x-gzip")) {
                try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(rawData))) {
                    return gis.readAllBytes();
                }
            } else if (encodingStyle.contains("bzip2")) {
                logger.warn("bzip2 encoding not supported for xar entry");
                return null;
            } else if (encodingStyle.contains("zlib") || encodingStyle.contains("deflate")) {
                try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(rawData))) {
                    return iis.readAllBytes();
                }
            }

            // No encoding or "application/octet-stream" → raw data
            return rawData;
        } catch (Exception e) {
            logger.warn("Failed to read xar file entry: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get direct child element (not recursive like getElementsByTagName).
     */
    private Element getDirectChildElement(Element parent, String tagName) {
        for (org.w3c.dom.Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && tagName.equalsIgnoreCase(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    /**
     * Get text content of a direct child element.
     */
    private String getDirectChildText(Element parent, String tagName) {
        Element child = getDirectChildElement(parent, tagName);
        return child != null ? child.getTextContent().trim() : null;
    }

    private EnterpriseApp.EnterpriseAppBuilder fallbackFromFilename(String originalFilename, EnterpriseApp.EnterpriseAppBuilder builder) {
        // Remove extension
        String baseName = originalFilename.replaceAll("\\.[^.]+$", "");
        builder.displayName(baseName);
        // Create a reasonable bundleId from filename
        String bundleId = "enterprise." + baseName.toLowerCase()
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "");
        builder.bundleId(bundleId);
        logger.warn("Could not parse PKG metadata. Using filename-derived values: displayName={}, bundleId={}",
                baseName, bundleId);
        return builder;
    }

    private boolean parseDistributionXml(byte[] xmlBytes, EnterpriseApp.EnterpriseAppBuilder builder) {
        try {
            // Log first few bytes to verify it's XML
            String preview = new String(xmlBytes, 0, Math.min(100, xmlBytes.length), java.nio.charset.StandardCharsets.UTF_8);
            logger.debug("Distribution XML preview: {}", preview.replaceAll("\\s+", " "));

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Allow doctypes but disable external entities for security
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder docBuilder = factory.newDocumentBuilder();
            Document doc = docBuilder.parse(new ByteArrayInputStream(xmlBytes));

            // Look for <pkg-ref id="com.example.app" version="1.0">
            NodeList pkgRefs = doc.getElementsByTagName("pkg-ref");
            logger.debug("Found {} pkg-ref elements in Distribution", pkgRefs.getLength());

            for (int i = 0; i < pkgRefs.getLength(); i++) {
                Element el = (Element) pkgRefs.item(i);
                String id = el.getAttribute("id");
                String version = el.getAttribute("version");
                if (id != null && !id.isBlank()) {
                    builder.bundleId(id);
                    if (version != null && !version.isBlank()) {
                        builder.version(version);
                    }

                    // Try to get display name from <title> element
                    NodeList titles = doc.getElementsByTagName("title");
                    if (titles.getLength() > 0) {
                        String title = titles.item(0).getTextContent();
                        if (title != null && !title.isBlank()) {
                            builder.displayName(title);
                        }
                    }
                    if (builder.build().getDisplayName() == null) {
                        builder.displayName(id);
                    }

                    logger.info("Parsed PKG Distribution: bundleId={}, version={}", id, version);
                    return true;
                }
            }

            logger.debug("No valid pkg-ref found in Distribution XML");
        } catch (Exception e) {
            logger.warn("Failed to parse Distribution XML: {}", e.getMessage());
        }
        return false;
    }

    private boolean parsePackageInfoXml(byte[] xmlBytes, EnterpriseApp.EnterpriseAppBuilder builder) {
        try {
            // Log first few bytes to verify it's XML
            String preview = new String(xmlBytes, 0, Math.min(100, xmlBytes.length), java.nio.charset.StandardCharsets.UTF_8);
            logger.debug("PackageInfo XML preview: {}", preview.replaceAll("\\s+", " "));

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Allow doctypes but disable external entities for security
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder docBuilder = factory.newDocumentBuilder();
            Document doc = docBuilder.parse(new ByteArrayInputStream(xmlBytes));

            // <pkg-info identifier="com.example.app" version="1.0">
            Element root = doc.getDocumentElement();
            logger.debug("PackageInfo root element: {}", root.getTagName());

            if ("pkg-info".equalsIgnoreCase(root.getTagName())) {
                String identifier = root.getAttribute("identifier");
                String version = root.getAttribute("version");
                if (identifier != null && !identifier.isBlank()) {
                    builder.bundleId(identifier);
                    if (version != null && !version.isBlank()) {
                        builder.version(version);
                    }
                    builder.displayName(identifier);
                    logger.info("Parsed PKG PackageInfo: bundleId={}, version={}", identifier, version);
                    return true;
                }
            }

            logger.debug("PackageInfo root is not pkg-info element");
        } catch (Exception e) {
            logger.warn("Failed to parse PackageInfo XML: {}", e.getMessage());
        }
        return false;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String plistString(NSDictionary dict, String key) {
        NSObject obj = dict.get(key);
        if (obj == null) return null;
        Object javaObj = obj.toJavaObject();
        return javaObj != null ? javaObj.toString() : null;
    }

    private String computeSha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException("HASH_ERROR", "SHA-256 not available", e);
        }
    }

}
