package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSDictionary;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Scep extends BasePayload {


    private String configurationName;
    // Inner PayloadContent fields
    private String challenge;           // "Challenge"
    private String keyType;             // "Key Type"  (e.g. "RSA")
    private Integer keyUsage;           // "Key Usage"
    private Integer keysize;            // "Keysize"
    private String name;                // "Name"
    private Object subject;             // "Subject"  (nested array structure)
    private Map<String, Object> subjectAltName; // "SubjectAltName"
    private String url;                 // "URL"
    private Integer retries;            // "Retries" (default 3)
    private Integer retryDelay;         // "RetryDelay" (default 10)
    private Boolean allowAllAppsAccess; // "AllowAllAppsAccess"
    private Boolean keyIsExtractable;   // "KeyIsExtractable"
    private String certTemplate;        // "CAInstanceName" (Microsoft CA)
    private byte[] caFingerprint;       // "CAFingerprint"

    @SuppressWarnings("unchecked")
    public static Scep createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }

        Scep scep = Scep.builder()
                .configurationName((String) map.get("configurationName"))
                .challenge((String) map.getOrDefault("challenge", null))
                .keyType((String) map.getOrDefault("keyType", "RSA"))
                .keyUsage(map.get("keyUsage") instanceof Number n ? n.intValue() : null)
                .keysize(map.get("keysize") instanceof Number n ? n.intValue() : null)
                .name((String) map.getOrDefault("name", null))
                .subject(map.get("subject")) // beklenen format: Apple dokümanındaki nested array
                .subjectAltName((Map<String, Object>) map.get("subjectAltName"))
                .url((String) map.getOrDefault("url", null))
                .retries(map.get("retries") instanceof Number n ? n.intValue() : 3)
                .retryDelay(map.get("retryDelay") instanceof Number n ? n.intValue() : 10)
                .allowAllAppsAccess(map.get("allowAllAppsAccess") instanceof Boolean b ? b : false)
                .keyIsExtractable(map.get("keyIsExtractable") instanceof Boolean b ? b : false)
                .certTemplate((String) map.getOrDefault("certTemplate", null))
                .caFingerprint(map.get("caFingerprint") instanceof String s ? java.util.Base64.getDecoder().decode(s.trim()) : null)
                .build();

        scep.setPayloadIdentifier(String.format("policy_scep-%s", policyId));
        scep.setPayloadType("com.apple.security.scep");
        scep.setPayloadUUID(UUID.randomUUID().toString());
        scep.setPayloadVersion(1);

        return scep;
    }

    public NSDictionary createPayload() {
        // İç SCEP bilgileri (PayloadContent içi)
        NSDictionary scepContent = new NSDictionary();

        if (this.challenge != null) {
            scepContent.put("Challenge", this.challenge);
        }
        if (this.keyType != null) {
            scepContent.put("Key Type", this.keyType);
        }
        if (this.keyUsage != null) {
            scepContent.put("Key Usage", this.keyUsage);
        }
        if (this.keysize != null) {
            scepContent.put("Keysize", this.keysize);
        }
        if (this.name != null) {
            scepContent.put("Name", this.name);
        }
        if (this.subject != null) {
            // subject’i UI’den gelen nested array yapısıyla aynen plist’e basıyoruz
            scepContent.put("Subject", this.subject);
        }

        if (this.subjectAltName != null && !this.subjectAltName.isEmpty()) {
            NSDictionary san = new NSDictionary();
            for (Map.Entry<String, Object> entry : this.subjectAltName.entrySet()) {
                if (entry.getValue() != null) {
                    san.put(entry.getKey(), entry.getValue().toString());
                }
            }
            scepContent.put("SubjectAltName", san);
        }

        if (this.url != null) {
            scepContent.put("URL", this.url);
        }
        if (this.retries != null) {
            scepContent.put("Retries", this.retries);
        }
        if (this.retryDelay != null) {
            scepContent.put("RetryDelay", this.retryDelay);
        }
        if (this.allowAllAppsAccess != null) {
            scepContent.put("AllowAllAppsAccess", this.allowAllAppsAccess);
        }
        if (this.keyIsExtractable != null) {
            scepContent.put("KeyIsExtractable", this.keyIsExtractable);
        }
        if (this.certTemplate != null) {
            scepContent.put("CAInstanceName", this.certTemplate);
        }
        if (this.caFingerprint != null) {
            scepContent.put("CAFingerprint", this.caFingerprint);
        }

        // Asıl payload dict (Profile.PayloadContent array’inde duran eleman)
        NSDictionary payload = new NSDictionary();
        payload.put("PayloadContent", scepContent);
        payload.put("PayloadIdentifier", this.getPayloadIdentifier());
        payload.put("PayloadType", this.getPayloadType()); // com.apple.security.scep
        payload.put("PayloadUUID", this.getPayloadUUID());
        payload.put("PayloadVersion", this.getPayloadVersion());

        return payload;
    }
}