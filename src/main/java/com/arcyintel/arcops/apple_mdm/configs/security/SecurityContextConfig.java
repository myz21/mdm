package com.arcyintel.arcops.apple_mdm.configs.security;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Propagates SecurityContext to @Async child threads so that
 * AuditorAware can read the JWT "username" claim in background threads.
 */
@Configuration
public class SecurityContextConfig {

    @PostConstruct
    public void init() {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }
}
