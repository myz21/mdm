package com.arcyintel.arcops.apple_mdm.models.cert.vpp.asset;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AssetAssignRequest {
    private String serialNumber;
    private List<String> adamIds;
}