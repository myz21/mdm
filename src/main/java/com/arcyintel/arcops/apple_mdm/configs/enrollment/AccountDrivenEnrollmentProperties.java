package com.arcyintel.arcops.apple_mdm.configs.enrollment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mdm.enrollment.account-driven")
public class AccountDrivenEnrollmentProperties {

    private String defaultAuthMethod = "simple";

    private SimpleAuth simple = new SimpleAuth();

    private OAuth2Defaults oauth2 = new OAuth2Defaults();

    @Data
    public static class SimpleAuth {
        private String realm = "ArcOps MDM";
    }

    @Data
    public static class OAuth2Defaults {
        private String redirectUrl = "apple-remotemanagement-user-login:/oauth2/redirection";
    }
}
