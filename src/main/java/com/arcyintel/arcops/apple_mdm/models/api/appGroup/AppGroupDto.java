package com.arcyintel.arcops.apple_mdm.models.api.appgroup;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "App Group response")
public class AppGroupDto {

    private UUID id;

    private String name;

    private String description;

    private Map<String, Object> metadata;

    @ArraySchema(schema = @Schema(implementation = AppGroupItemDto.class))
    private List<AppGroupItemDto> items;

    private Date creationDate;

    private Date lastModifiedDate;
}
