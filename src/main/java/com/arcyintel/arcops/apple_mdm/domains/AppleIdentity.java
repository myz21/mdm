package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AuditableTimestamps;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "apple_identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLDelete(sql = "UPDATE apple_identity SET status = 'DELETED' WHERE id = ?")
@SQLRestriction("status <> 'DELETED'")
public class AppleIdentity extends AuditableTimestamps {

    @Column(nullable = false)
    private String username;

    @Column
    private String email;

    @Column(name = "full_name")
    private String fullName;

    @Column(nullable = false)
    private String source;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column
    @Builder.Default
    private String status = "ACTIVE";

    @OneToMany(mappedBy = "identity")
    @Builder.Default
    private Set<AppleAccount> accounts = new HashSet<>();
}
