package com.arcyintel.arcops.apple_mdm.utils.certs;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public final class PemUtil {

    private PemUtil() {
    }

    /**
     * Resource üzerinden KeyPair yükleme.
     */
    public static KeyPair loadKeyPair(Resource resource) throws Exception {
        if (resource == null) {
            throw new IllegalArgumentException("Resource for key pair is null");
        }
        if (!resource.exists()) {
            throw new IllegalArgumentException("Key resource does not exist: " + resource);
        }

        try (InputStream in = resource.getInputStream();
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             PEMParser pemParser = new PEMParser(reader)) {

            Object obj = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

            if (obj instanceof PEMKeyPair pemKeyPair) {
                return converter.getKeyPair(pemKeyPair);
            } else if (obj instanceof PrivateKeyInfo privateKeyInfo) {
                // Dosyada sadece private key varsa (public key'i CA sertifikasından alabilirsin)
                PrivateKey privateKey = converter.getPrivateKey(privateKeyInfo);
                return new KeyPair(null, privateKey);
            } else {
                throw new IllegalArgumentException("Unsupported key format in resource: " + resource);
            }
        }
    }

    /**
     * Resource üzerinden X509 sertifika yükleme.
     */
    public static X509Certificate loadCertificate(Resource resource) throws Exception {
        if (resource == null) {
            throw new IllegalArgumentException("Resource for certificate is null");
        }
        if (!resource.exists()) {
            throw new IllegalArgumentException("Certificate resource does not exist: " + resource);
        }

        try (InputStream in = resource.getInputStream()) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(in);
        }
    }

    // ---- Eski String path'li versiyonları da istersen koruyalım ----

    /**
     * String path üzerinden KeyPair yükleme (Resource metoduna delege ediyor).
     */
    public static KeyPair loadKeyPair(String pemPath) throws Exception {
        if (pemPath == null) {
            throw new IllegalArgumentException("pemPath is null");
        }
        // file system path için
        Path path = Path.of(pemPath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Key file does not exist: " + pemPath);
        }

        try (InputStream in = Files.newInputStream(path);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             PEMParser pemParser = new PEMParser(reader)) {

            Object obj = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

            if (obj instanceof PEMKeyPair pemKeyPair) {
                return converter.getKeyPair(pemKeyPair);
            } else if (obj instanceof PrivateKeyInfo privateKeyInfo) {
                PrivateKey privateKey = converter.getPrivateKey(privateKeyInfo);
                return new KeyPair(null, privateKey);
            } else {
                throw new IllegalArgumentException("Unsupported key format in " + pemPath);
            }
        }
    }

    /**
     * String path üzerinden X509 sertifika yükleme (Resource metoduna delege ediyor).
     */
    public static X509Certificate loadCertificate(String pemPath) throws Exception {
        if (pemPath == null) {
            throw new IllegalArgumentException("pemPath is null");
        }
        Path path = Path.of(pemPath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Certificate file does not exist: " + pemPath);
        }

        try (InputStream in = Files.newInputStream(path)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(in);
        }
    }
}