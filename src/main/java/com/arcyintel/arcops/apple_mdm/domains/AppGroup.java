package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.Auditable;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * AppGroup groups multiple apps (VPP or Enterprise) under a named collection.
 * - name, description: group metadata
 * - items: list of apps with their type and identifiers
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Table(name = "app_group")
public class AppGroup extends Auditable<String> {

    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    @Column(name = "description", length = 1024)
    private String description;

    /**
     * Optional: tags or extra metadata as JSON for future needs
     */
    @Column(name = "metadata")
    @JdbcTypeCode(SqlTypes.JSON)
    private java.util.Map<String, Object> metadata;

    /**
     * Items in this group.
     * Stored as separate entity to extend with Enterprise-specific fields later
     * without schema-breaking changes.
     */
    @OneToMany(mappedBy = "appGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "order_idx")
    @com.fasterxml.jackson.annotation.JsonManagedReference
    private List<AppGroupItem> items = new ArrayList<>();

    public enum AppType {
        VPP,
        ENTERPRISE
    }
}