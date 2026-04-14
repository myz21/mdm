package com.arcyintel.arcops.apple_mdm.services.apple.abm;

import com.arcyintel.arcops.apple_mdm.domains.ItunesAppMeta;
import com.arcyintel.arcops.apple_mdm.models.cert.vpp.asset.AssetAssignRequest;
import com.arcyintel.arcops.apple_mdm.models.cert.vpp.asset.Assignment;
import com.arcyintel.arcops.apple_mdm.models.cert.vpp.asset.VppAssetsResponse;
import com.arcyintel.arcops.apple_mdm.models.cert.vpp.user.VppUser;

import java.util.List;
import java.util.Map;

public interface AppleVppService {
    // Asset Operations
    List<ItunesAppMeta> fetchVppAssets() throws Exception;

    void assignAssetsToDevices(List<AssetAssignRequest> requests) throws Exception;

    void disassociateAssetsFromDevices(List<AssetAssignRequest> requests) throws Exception;

    void revokeAssetsFromDevice(String serialNumber) throws Exception;

    // Assignment Operations
    List<Assignment> fetchAllAssignments() throws Exception;

    List<VppUser> fetchAllVppUsers() throws Exception;

    // Client Config (org info)
    Map<String, String> getClientConfig() throws Exception;

    // Background Synchronization (Metadata)
    void syncItunesAppMeta(VppAssetsResponse vppAssetsResponse) throws Exception;
}