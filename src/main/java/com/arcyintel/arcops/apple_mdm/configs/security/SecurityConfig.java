package com.arcyintel.arcops.apple_mdm.configs.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AgentTokenFilter agentTokenFilter;
    private final PlatformPermissionFilter platformPermissionFilter;

    /**
     * MDM enrollment paths — no OAuth2 JWT filter.
     * Apple devices send Bearer tokens that are UUID enrollment tokens (not JWTs).
     * The JWT filter would reject these, so MDM paths must bypass it entirely.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain mdmFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(
                        "/.well-known/**",
                        "/mdm/account-enrollment/**",
                        "/mdm/enrollment/**",
                        "/mdm/checkin/**",
                        "/mdm/connect/**",
                        "/agent/**",
                        "/vnc-tunnel/ws",
                        "/screen-share/ws",
                        "/remote-terminal/ws"
                )
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .addFilterBefore(agentTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Default filter chain — OAuth2 JWT validation for internal API calls.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(new CustomJwtAuthenticationConverter())
                        )
                )
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .addFilterAfter(platformPermissionFilter, org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}