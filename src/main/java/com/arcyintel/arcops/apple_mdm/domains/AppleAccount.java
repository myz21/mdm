package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.Auditable;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "apple_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLDelete(sql = "UPDATE apple_account SET status = 'DELETED' WHERE id = ?")
@SQLRestriction("status <> 'DELETED'")
public class AppleAccount extends Auditable<String> {

    @Column(nullable = false)
    private String username;

    @Column
    private String email;

    @Column(name = "managed_apple_id")
    private String managedAppleId;

    @Column(name = "full_name")
    private String fullName;

    @Column
    @Builder.Default
    private String status = "ACTIVE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_id")
    private AppleIdentity identity;

    @ManyToMany
    @JoinTable(
            name = "apple_account_devices",
            joinColumns = @JoinColumn(name = "account_id"),
            inverseJoinColumns = @JoinColumn(name = "device_id")
    )
    @Builder.Default
    private Set<AppleDevice> devices = new HashSet<>();
}