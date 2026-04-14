package com.arcyintel.arcops.apple_mdm.configs.security;

import com.arcyintel.arcops.apple_mdm.utils.certs.PemUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

@Configuration
public class ScepConfig {

    @Value("${mdm.cert.paths.caKey}")
    private Resource caKeyResource;

    @Value("${mdm.cert.paths.caCert}")
    private Resource caCertResource;

    @Bean
    public KeyPair caKeyPair() throws Exception {
        // Artık direkt Resource üzerinden oku
        return PemUtil.loadKeyPair(caKeyResource);
    }

    @Bean
    public X509Certificate caCertificate() throws Exception {
        return PemUtil.loadCertificate(caCertResource);
    }
}