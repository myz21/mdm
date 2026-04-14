package com.arcyintel.arcops.apple_mdm.services.app;

import com.arcyintel.arcops.apple_mdm.models.api.enterpriseapp.AppAnalyzeResponse;
import com.arcyintel.arcops.apple_mdm.models.api.enterpriseapp.ConfirmUploadRequest;
import com.arcyintel.arcops.apple_mdm.models.api.enterpriseapp.GetEnterpriseAppDto;
import com.arcyintel.arcops.commons.api.DynamicListRequestDto;
import org.springframework.core.io.Resource;
import org.springframework.data.web.PagedModel;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

public interface EnterpriseAppService {

    /** Single-step upload (legacy, kept for backward compatibility). */
    GetEnterpriseAppDto upload(MultipartFile file);

    /** Phase 1: Upload to temp storage and parse metadata for user review. */
    AppAnalyzeResponse uploadAndAnalyze(MultipartFile file);

    /** Phase 2: User confirms metadata → move to permanent storage, save entity. */
    GetEnterpriseAppDto confirmUpload(String tempId, ConfirmUploadRequest request);

    /** Cancel a pending upload — delete temp file. */
    void cancelUpload(String tempId);

    GetEnterpriseAppDto getById(String id);

    GetEnterpriseAppDto getByBundleId(String bundleId);

    PagedModel<Map<String, Object>> list(DynamicListRequestDto request);

    Resource download(UUID id);

    void delete(UUID id);

    /**
     * Generates an Apple manifest plist for enterprise app installation.
     * The manifest contains metadata and download URL for the app.
     *
     * @param id the enterprise app UUID
     * @return manifest plist as XML string
     */
    String getManifest(UUID id);

    /**
     * Returns the manifest URL for an enterprise app.
     * Used by InstallApplication command with ManifestURL.
     *
     * @param id the enterprise app UUID
     * @return full manifest URL
     */
    String getManifestUrl(UUID id);

    /**
     * Find enterprise app by bundle ID (for policy installation).
     *
     * @param bundleId the bundle identifier
     * @return enterprise app or null if not found
     */
    GetEnterpriseAppDto findByBundleIdOrNull(String bundleId);
}
