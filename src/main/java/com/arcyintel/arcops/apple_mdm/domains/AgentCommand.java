package com.arcyintel.arcops.apple_mdm.domains;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "agent_command")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "command_uuid", unique = true, nullable = false)
    private String commandUuid;

    @Column(name = "device_identifier", nullable = false)
    private String deviceIdentifier;

    @Column(name = "command_type", nullable = false)
    private String commandType;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Type(JsonType.class)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Type(JsonType.class)
    @Column(name = "result", columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "request_time", nullable = false)
    @Builder.Default
    private Instant requestTime = Instant.now();

    @Column(name = "response_time")
    private Instant responseTime;

    @CreatedBy
    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
