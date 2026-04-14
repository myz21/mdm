package com.arcyintel.arcops.apple_mdm.models.api.appgroup;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Create/Update App Group payload")
public class AppGroupUpsertDto {

    @Schema(description = "Group name (unique)", example = "social")
    private String name;

    @Schema(description = "Description", example = "Common social apps")
    private String description;

    @Schema(description = "Arbitrary metadata for UI/filters")
    private Map<String, Object> metadata;

    @ArraySchema(schema = @Schema(implementation = AppGroupItemDto.class))
    private List<AppGroupItemDto> items;
}
