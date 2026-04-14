package com.arcyintel.arcops.apple_mdm.services.apple.cert;

import com.arcyintel.arcops.apple_mdm.services.apple.cert.ApplePushCredentialService;
import com.arcyintel.arcops.commons.exceptions.BusinessException;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.security.auth.x500.X500Principal;
import java.io.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Primary
public class ApplePushCredentialServiceImpl implements ApplePushCredentialService {

    private static final Logger logger = LoggerFactory.getLogger(ApplePushCredentialServiceImpl.class);
    @Value("${mdm.cert.p12-password:}")
    private String p12Password;
    // APNs certificate; file path: src/main/resources/apple/MDM_Certificate.pem
    @Value("file:certs/apple/MDM_Certificate.pem")
    private Resource mdmCertResource;
    // Chain file; file path: src/main/resources/apple/chain.pem
    @Value("file:certs/apple/chain.pem")
    private Resource chainResource;
    // Output path for the generated PKCS#12 file
    @Value("${mdm.apple.p12.output.path:certs/apple/mdm_customer_push.p12}")
    private String outputP12Path;
    @Value("${mdm.cert.paths.pushCert}")
    private Resource mdmPushCertResource;

    public ApplePushCredentialServiceImpl() {
        // Add BouncyCastle provider
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * /**
     * Creates a PKCS#12 file using the customer private key created by CertificationService,
     * MDM_Certificate.pem, and chain.pem files.
     */
    @Async
    public void generateApnsP12() throws Exception {

        logger.info("Starting the generation of the APNs PKCS#12 file...");
        File p12File = new File(outputP12Path);
        if (p12File.exists()) {
            logger.info("The APNs PKCS#12 file already exists at '{}'. It will be overwritten.", p12File.getAbsolutePath());
        }

        logger.info("Preparing to load the APNs certificate and certificate chain...");
        // 1. Load the customer private key from customer.key
        logger.debug("Attempting to load the customer private key from the file 'customer.key'.");
        PrivateKey customerPrivateKey = loadCustomerPrivateKey();
        if (customerPrivateKey == null) {
            logger.error("Failed to load the customer private key. Ensure the 'customer.key' file exists and is correctly configured.");
            throw new BusinessException("CERT_ERROR", "Customer private key is missing. Generate CSR first.");
        }
        logger.info("Successfully loaded the customer private key.");

        // 2. Load the APNs certificate (MDM_Certificate.pem)
        logger.debug("Attempting to load the APNs certificate from the resource '{}'.", mdmCertResource.getFilename());
        X509Certificate mdmCert = loadCertificate(mdmCertResource);

        if (mdmCert == null) {
            logger.error("The APNs certificate file '{}' could not be found or loaded. Ensure the file exists and is accessible.", mdmCertResource.getFilename());
            throw new BusinessException("CERT_ERROR", "MDM_Certificate.pem is missing.");
        }
        logger.info("Successfully loaded the APNs certificate from '{}'.", mdmCertResource.getFilename());

        // 3. Load the certificate chain (chain.pem)
        logger.debug("Attempting to load the certificate chain from the resource '{}'.", chainResource.getFilename());
        X509Certificate[] rawChainCerts = loadCertificates(chainResource);
        logger.info("Successfully loaded {} certificates from the resource '{}'.", rawChainCerts.length, chainResource.getFilename());

        // Build ordered certificate chain: leaf -> intermediate(s) -> root
        Map<X500Principal, X509Certificate> chainMap = new HashMap<>();
        for (X509Certificate cert : rawChainCerts) {
            if (!cert.getSubjectX500Principal().equals(mdmCert.getSubjectX500Principal())) {
                chainMap.put(cert.getSubjectX500Principal(), cert);
            }
        }
        List<X509Certificate> orderedChain = new ArrayList<>();
        X509Certificate current = mdmCert;
        while (true) {
            X500Principal issuer = current.getIssuerX500Principal();
            X509Certificate nextCert = chainMap.remove(issuer);
            if (nextCert == null) {
                break;
            }
            orderedChain.add(nextCert);
            current = nextCert;
        }
        X509Certificate[] fullChain = new X509Certificate[orderedChain.size() + 1];
        fullChain[0] = mdmCert;
        for (int i = 0; i < orderedChain.size(); i++) {
            fullChain[i + 1] = orderedChain.get(i);
        }
        logger.info("Ordered certificate chain built; total certificates: {}", fullChain.length);

        // 5. Create PKCS#12 keystore
        logger.debug("Initializing a new PKCS#12 keystore instance.");
        KeyStore pkcs12 = KeyStore.getInstance("PKCS12");
        pkcs12.load(null, null);
        logger.info("PKCS#12 keystore instance initialized successfully.");

        logger.debug("Adding the private key and full certificate chain to the PKCS#12 keystore.");
        pkcs12.setKeyEntry("mdm_customer_push", customerPrivateKey, p12Password.toCharArray(), fullChain);
        logger.info("Private key and certificate chain have been added to the PKCS#12 keystore successfully.");

        // 6. Save the PKCS#12 keystore to disk
        File outputFile = new File(outputP12Path);
        logger.debug("Preparing to save the PKCS#12 keystore to the file '{}'.", outputFile.getAbsolutePath());

        boolean res = outputFile.getParentFile().mkdirs();
        if (!res && !outputFile.getParentFile().exists()) {
            logger.error("Failed to create the output directory: '{}'. Ensure the directory path is correct and writable.", outputFile.getParent());
            throw new IOException("Failed to create output directory: " + outputFile.getParent());
        }
        logger.info("Output directory verified or created successfully: '{}'.", outputFile.getParent());

        logger.debug("Saving the PKCS#12 keystore to the file '{}'.", outputFile.getAbsolutePath());
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            pkcs12.store(fos, p12Password.toCharArray());
            logger.info("PKCS#12 keystore has been successfully saved to the file '{}'.", outputFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("An error occurred while saving the PKCS#12 keystore to the file '{}'. Error: {}", outputFile.getAbsolutePath(), e.getMessage(), e);
            throw e;
        }
    }

    private PrivateKey loadCustomerPrivateKey() throws Exception {

        logger.debug("Starting to load the customer private key from 'customer.key'.");
        File keyFile = new File("certs/apple/customer.key");

        if (!keyFile.exists()) {
            logger.error("The 'customer.key' file was not found at the expected location: {}", keyFile.getAbsolutePath());
            throw new FileNotFoundException("The 'customer.key' file is missing. Path: " + keyFile.getAbsolutePath());
        }

        try (FileReader fileReader = new FileReader(keyFile);
             PEMParser pemParser = new PEMParser(fileReader)) {

            logger.debug("Successfully opened the 'customer.key' file. Parsing the file content.");
            Object object = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

            logger.debug("Converting the parsed PEM object into a PrivateKey instance.");
            if (object instanceof PEMKeyPair pemKeyPair) {
                PrivateKey privateKey = converter.getKeyPair(pemKeyPair).getPrivate();
                logger.info("Successfully loaded the customer private key from 'customer.key'.");
                return privateKey;
            } else if (object instanceof PrivateKeyInfo privateKeyInfo) {
                PrivateKey privateKey = converter.getPrivateKey(privateKeyInfo);
                logger.info("Successfully loaded the customer private key from 'customer.key'.");
                return privateKey;
            } else {
                logger.error("The 'customer.key' file contains an unsupported key format. Unable to load the private key.");
                throw new IllegalArgumentException("Unsupported key format in 'customer.key'.");
            }
        } catch (Exception e) {
            logger.error("An error occurred while loading the customer private key from 'customer.key'. Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    private X509Certificate loadCertificate(Resource resource) throws Exception {
        if (resource == null) {
            logger.error("The provided resource is null. Unable to load the certificate.");
            throw new IllegalArgumentException("Provided resource is null.");
        }
        if (!resource.exists()) {
            logger.error("The certificate resource '{}' does not exist. Please verify the file path and ensure the file is accessible.", resource.getFilename());
            throw new FileNotFoundException("Certificate resource not found: " + resource.getFilename());
        }
        logger.info("Attempting to load the certificate from the resource '{}'.", resource.getFilename());
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream is = resource.getInputStream()) {
            logger.debug("Successfully opened the input stream for the resource '{}'. Generating the X.509 certificate.", resource.getFilename());
            X509Certificate certificate = (X509Certificate) cf.generateCertificate(is);
            logger.info("Successfully generated the X.509 certificate from the resource '{}'.", resource.getFilename());
            return certificate;
        } catch (Exception e) {
            logger.error("An error occurred while generating the X.509 certificate from the resource '{}'. Error: {}", resource.getFilename(), e.getMessage(), e);
            throw e;
        }
    }

    private X509Certificate[] loadCertificates(Resource resource) throws Exception {
        if (!resource.exists()) {
            logger.error("The certificate resource '{}' does not exist. Please verify the file path and ensure the file is accessible.", resource.getFilename());
            throw new FileNotFoundException("Certificate resource not found: " + resource.getFilename());
        }
        logger.info("Starting to load certificates from the resource '{}'.", resource.getFilename());
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream is = resource.getInputStream()) {
            logger.debug("Successfully opened the input stream for the resource '{}'. Generating certificates.", resource.getFilename());
            @SuppressWarnings("unchecked")
            Collection<X509Certificate> certs = (Collection<X509Certificate>) cf.generateCertificates(is);
            logger.info("Successfully loaded {} certificates from the resource '{}'.", certs.size(), resource.getFilename());
            return certs.toArray(new X509Certificate[0]);
        } catch (Exception e) {
            logger.error("An error occurred while loading certificates from the resource '{}'. Error: {}", resource.getFilename(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * /**
     * Parses the "com.apple.mgmt.External..." Topic from MDM__Certificate.pem.
     */
    public String getPushTopic() throws Exception {

        logger.debug("Starting the process to extract the push topic from the MDM push certificate...");

        // 1) Read the source file from the provided resource.
        try (InputStream is = mdmPushCertResource.getInputStream()) {
            byte[] certBytes = is.readAllBytes();
            logger.debug("Successfully read {} bytes from the MDM push certificate resource '{}'.", certBytes.length, mdmPushCertResource.getFilename());

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate x509;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(certBytes)) {
                logger.debug("Attempting to generate an X509Certificate from the certificate bytes.");
                x509 = (X509Certificate) cf.generateCertificate(bais);
                logger.info("Successfully generated an X509Certificate from the MDM push certificate.");
            }

            // 2) Search within the Subject Alternative Names for the push topic.
            Collection<List<?>> altNames = x509.getSubjectAlternativeNames();
            if (altNames != null) {
                logger.debug("Found {} entries in the Subject Alternative Names of the certificate.", altNames.size());
                for (List<?> entry : altNames) {
                    // entry: [type, value]
                    Object value = entry.get(1);
                    if (value instanceof String str && str.contains("com.apple.mgmt.External")) {
                        logger.info("Push topic found in Subject Alternative Names: {}", str);
                        return str; // "com.apple.mgmt.External.XXXX-XXXX-..."
                    }
                }
                logger.debug("No push topic found in the Subject Alternative Names.");
            } else {
                logger.debug("The Subject Alternative Names section is empty or not present in the certificate.");
            }

            // 3) Search within the Subject DN for the push topic.
            String subj = x509.getSubjectX500Principal().getName();
            logger.debug("Extracting Subject DN from the certificate: {}", subj);
            if (subj.contains("com.apple.mgmt.External")) {
                logger.debug("Found 'com.apple.mgmt.External' in the Subject DN.");
                Pattern p = Pattern.compile("(com\\.apple\\.mgmt\\.External\\.[^,\\s]+)");
                Matcher m = p.matcher(subj);
                if (m.find()) {
                    String pushTopic = m.group(1);
                    logger.info("Push topic extracted from Subject DN: {}", pushTopic);
                    return pushTopic;
                }
                logger.debug("No matching push topic pattern found in the Subject DN.");
            } else {
                logger.debug("'com.apple.mgmt.External' not found in the Subject DN.");
            }

            logger.error("Failed to extract the push topic. It was not found in either the Subject Alternative Names or the Subject DN.");
            throw new BusinessException("CERT_ERROR", "Push topic (com.apple.mgmt.External) not found in the MDM push certificate.");
        }
    }
}
