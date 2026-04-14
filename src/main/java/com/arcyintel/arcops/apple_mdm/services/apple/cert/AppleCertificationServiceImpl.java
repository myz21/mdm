package com.arcyintel.arcops.apple_mdm.services.apple.cert;

import com.arcyintel.arcops.apple_mdm.services.apple.cert.AppleCertificationService;
import com.arcyintel.arcops.apple_mdm.utils.storage.RenameMultipartFile;
import com.arcyintel.arcops.apple_mdm.utils.storage.StorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

@RequiredArgsConstructor
@Service
@Primary
public class AppleCertificationServiceImpl implements AppleCertificationService {
    private static final Logger logger = LoggerFactory.getLogger(AppleCertificationServiceImpl.class);
    private static final String CERTS_DIR = "certs/apple";
    private static final String CUSTOMER_PRIVATE_KEY_FILE = "customer.key";
    private static final int RSA_KEY_SIZE = 2048;
    private static final String SIGN_ALGORITHM = "SHA256withRSA";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final StorageService storageService;
    public PrivateKey customerPrivateKey;
    @Value("${mdm.cert.subject.country}")
    private String country;
    @Value("${mdm.cert.subject.state}")
    private String state;
    @Value("${mdm.cert.subject.locality}")
    private String locality;
    @Value("${mdm.cert.subject.organization}")
    private String organization;
    @Value("${mdm.cert.subject.organizationalUnit}")
    private String organizationalUnit;
    @Value("${mdm.cert.subject.commonName}")
    private String commonName;
    @Value("${mdm.cert.subject.email}")
    private String email;
    @Value("${mdm.cert.paths.vendorKey}")
    private Resource vendorKeyResource;
    @Value("${mdm.cert.paths.mdmPem}")
    private Resource mdmPemResource;
    @Value("${mdm.cert.paths.appleWWDRCAG3}")
    private Resource appleWWDRCAG3Resource;
    @Value("${mdm.cert.paths.appleIncRootCertificate}")
    private Resource appleIncRootCertificateResource;
    @Value("${mdm.cert.paths.directory:certs/apple}")
    private String uploadDirectory;

    @Value("${mdm.vendor.renewal.secret:ArcOps_Vendor_Renewal_2024!}")
    private String vendorRenewalSecret;

    private static final String VENDOR_CERT_BACKUP_SUFFIX = ".backup";

    @PostConstruct
    public void init() {
        try {
            logger.info("Initializing AppleCertificationService: Attempting to load the customer private key from disk...");
            this.customerPrivateKey = loadPrivateKeyFromDisk();
            logger.info("Customer private key successfully loaded from disk.");
        } catch (Exception e) {
            logger.error("Failed to load the customer private key from disk. Error: {}", e.getMessage(), e);
        }
    }

    public String generatePlist(String companyName, String email) throws Exception {

        logger.info("Starting the process to generate the push certificate request plist.");

        // Use provided values or fall back to config
        String effectiveOrganization = (companyName != null && !companyName.isBlank()) ? companyName : this.organization;
        String effectiveEmail = (email != null && !email.isBlank()) ? email : this.email;
        String effectiveCommonName = (companyName != null && !companyName.isBlank()) ? companyName + " MDM" : this.commonName;

        logger.info("Using organization: {}, email: {}, commonName: {}", effectiveOrganization, effectiveEmail, effectiveCommonName);

        logger.info("Generating a new RSA key pair for the customer.");
        KeyPair keyPair = generateKeyPair();
        this.customerPrivateKey = keyPair.getPrivate();
        savePrivateKeyToDisk(this.customerPrivateKey);
        logger.info("Customer private key successfully generated and saved to disk.");

        logger.info("Creating a Certificate Signing Request (CSR) for the customer.");
        PKCS10CertificationRequest csr = generateCSR(keyPair.getPublic(), effectiveOrganization, effectiveCommonName, effectiveEmail);
        logger.info("CSR successfully created for the customer.");

        logger.info("Loading the vendor private key from the configured resource.");
        PrivateKey vendorPrivateKey = loadPrivateKey(vendorKeyResource);
        logger.info("Vendor private key successfully loaded.");

        logger.info("Signing the CSR using the vendor private key.");
        String csrBase64 = wrapBase64(encodeBase64(csr.getEncoded()));
        String signatureBase64 = wrapBase64(signData(csr.getEncoded(), vendorPrivateKey));
        logger.info("CSR successfully signed. Base64-encoded CSR and signature generated.");

        logger.info("Reading Apple CA certificates and MDM PEM files to build the certificate chain.");
        String certChain = readCertificates();
        logger.info("Certificate chain successfully read and constructed.");

        logger.info("Creating the final plist file with the CSR, signature, and certificate chain.");
        String plist = createPlist(csrBase64, signatureBase64, certChain);
        logger.info("Plist file successfully created.");

        logger.info("Encoding the plist file to Base64 format for output.");
        String encodedPlist = encodeBase64(plist.getBytes(StandardCharsets.UTF_8));
        logger.info("Plist generation process completed successfully.");

        return encodedPlist;
    }


    public void uploadCertificate(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            logger.error("The uploaded certificate file is empty. Operation aborted.");
            throw new IOException("The uploaded certificate file is empty.");
        }
        logger.info("Starting the upload process for the certificate file: {}", file.getOriginalFilename());

        RenameMultipartFile renamedFile = new RenameMultipartFile(file, "MDM_Certificate.pem");
        logger.debug("Renamed the uploaded file to: MDM_Certificate.pem");

        storageService.store(renamedFile, "apple");
        logger.info("Certificate file '{}' successfully uploaded and stored in the 'apple' directory.", renamedFile.getOriginalFilename());
    }


    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        logger.info("Initializing RSA KeyPairGenerator with key size: {}", RSA_KEY_SIZE);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(RSA_KEY_SIZE);
        logger.info("Generating RSA key pair.");
        KeyPair keyPair = generator.generateKeyPair();
        logger.info("RSA key pair successfully generated.");
        return keyPair;
    }

    private PKCS10CertificationRequest generateCSR(PublicKey publicKey, String effectiveOrganization, String effectiveCommonName, String effectiveEmail) throws Exception {
        logger.info("Starting the process to generate a Certificate Signing Request (CSR).");

        String subjectStr = String.format("C=%s, ST=%s, L=%s, O=%s, OU=%s, CN=%s, EMAILADDRESS=%s",
                country, state, locality, effectiveOrganization, organizationalUnit, effectiveCommonName, effectiveEmail);
        logger.debug("Constructed subject string for CSR: {}", subjectStr);

        X500Name subject = new X500Name(subjectStr);
        logger.debug("X500Name object created for subject: {}", subject);

        logger.info("Initializing content signer with the customer's private key.");
        ContentSigner signer = new JcaContentSignerBuilder(SIGN_ALGORITHM).build(customerPrivateKey);
        logger.debug("Content signer successfully initialized with algorithm: {}", SIGN_ALGORITHM);

        logger.info("Building the CSR using the provided public key and subject.");
        PKCS10CertificationRequest csr = new JcaPKCS10CertificationRequestBuilder(subject, publicKey).build(signer);
        logger.info("CSR successfully generated.");

        return csr;
    }

    private String createPlist(String csr, String signature, String certChain) {
        logger.info("Starting to create the plist file with the provided CSR, signature, and certificate chain.");
        logger.debug("CSR: {}", csr);
        logger.debug("Signature: {}", signature);
        logger.debug("Certificate Chain: {}", certChain);

        String plist = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>PushCertRequestCSR</key><string>%s</string>
                    <key>PushCertSignature</key><string>%s</string>
                    <key>PushCertCertificateChain</key><string>%s</string>
                </dict>
                </plist>""", csr, signature, certChain);

        logger.debug("Generated plist content: {}", plist);

        return plist;
    }


    private String encodeBase64(byte[] data) {
        logger.info("Encoding data to Base64 format.");
        String encodedData = Base64.getEncoder().encodeToString(data);
        logger.debug("Data successfully encoded to Base64: {}", encodedData);
        return encodedData;
    }

    private String wrapBase64(String input) {
        logger.debug("Starting to wrap Base64-encoded string into 64-character lines.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i += 64) {
            sb.append(input, i, Math.min(i + 64, input.length())).append("\n");
        }
        logger.debug("Base64 string successfully wrapped into 64-character lines.");
        return sb.toString();
    }

    private void savePrivateKeyToDisk(PrivateKey privateKey) throws IOException {
        logger.info("Attempting to save the private key to disk at directory: {}", CERTS_DIR);
        File directory = new File(CERTS_DIR);
        if (!directory.exists()) {
            logger.debug("Directory '{}' does not exist. Attempting to create it.", CERTS_DIR);
            if (!directory.mkdirs()) {
                logger.error("Failed to create directory: {}", CERTS_DIR);
                throw new IOException("Could not create directory: " + CERTS_DIR);
            }
            logger.info("Directory '{}' successfully created.", CERTS_DIR);
        } else {
            logger.debug("Directory '{}' already exists.", CERTS_DIR);
        }

        String privateKeyFilePath = CERTS_DIR + "/" + CUSTOMER_PRIVATE_KEY_FILE;
        logger.info("Saving private key to file: {}", privateKeyFilePath);
        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(privateKeyFilePath))) {
            writer.writeObject(privateKey);
            logger.info("Private key successfully saved to file: {}", privateKeyFilePath);
        } catch (IOException e) {
            logger.error("Failed to save private key to file: {}. Error: {}", privateKeyFilePath, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void reloadCustomerPrivateKey() throws Exception {
        logger.info("Reloading customer private key from disk (hot-reload)...");
        this.customerPrivateKey = loadPrivateKeyFromDisk();
        logger.info("Customer private key successfully reloaded.");
    }

    public PrivateKey loadPrivateKeyFromDisk() throws Exception {

        logger.info("Starting the process to load the customer private key from disk.");

        String privateKeyFilePath = CERTS_DIR + "/" + CUSTOMER_PRIVATE_KEY_FILE;
        logger.debug("Private key file path resolved to: {}", privateKeyFilePath);

        try (Reader reader = new FileReader(privateKeyFilePath);
             PEMParser parser = new PEMParser(reader)) {

            logger.info("Attempting to read the private key from file: {}", privateKeyFilePath);
            Object object = parser.readObject();

            if (object == null) {
                logger.error("No private key found in the file: {}. Please ensure the file contains a valid private key.", privateKeyFilePath);
                throw new IllegalArgumentException("Private key not found in file: " + privateKeyFilePath);
            }

            logger.info("Private key successfully read from file. Converting to PrivateKey object.");
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(new BouncyCastleProvider());

            if (object instanceof PEMKeyPair kp) {
                logger.debug("Detected PEMKeyPair format. Converting to PrivateKey.");
                return converter.getKeyPair(kp).getPrivate();
            } else if (object instanceof PrivateKeyInfo pk) {
                logger.debug("Detected PrivateKeyInfo format. Converting to PrivateKey.");
                return converter.getPrivateKey(pk);
            } else {
                logger.error("Invalid private key format detected in file: {}. Supported formats are PEMKeyPair and PrivateKeyInfo.", privateKeyFilePath);
                throw new IllegalArgumentException("Invalid private key format: " + privateKeyFilePath);
            }
        } catch (FileNotFoundException e) {
            logger.error("Private key file not found at path: {}. Ensure the file exists and the path is correct.", privateKeyFilePath, e);
            throw e;
        } catch (IOException e) {
            logger.error("An I/O error occurred while reading the private key file: {}. Error: {}", privateKeyFilePath, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("An unexpected error occurred while loading the private key from file: {}. Error: {}", privateKeyFilePath, e.getMessage(), e);
            throw e;
        }
    }

    private PrivateKey loadPrivateKey(Resource resource) throws Exception {
        logger.info("Starting the process to load a private key from the resource: {}", resource.getFilename());
        try (Reader reader = new InputStreamReader(resource.getInputStream());
             PEMParser parser = new PEMParser(reader)) {

            logger.debug("Reading the private key content from the resource: {}", resource.getFilename());
            Object object = parser.readObject();

            if (object == null) {
                logger.error("No private key found in the resource: {}. Ensure the file contains a valid private key.", resource.getFilename());
                throw new IllegalArgumentException("Private key not found in resource: " + resource.getFilename());
            }

            logger.info("Private key successfully read from the resource: {}. Converting to PrivateKey object.", resource.getFilename());
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(new BouncyCastleProvider());

            if (object instanceof PEMKeyPair kp) {
                logger.debug("Detected PEMKeyPair format in the resource: {}. Converting to PrivateKey.", resource.getFilename());
                return converter.getKeyPair(kp).getPrivate();
            } else if (object instanceof PrivateKeyInfo pk) {
                logger.debug("Detected PrivateKeyInfo format in the resource: {}. Converting to PrivateKey.", resource.getFilename());
                return converter.getPrivateKey(pk);
            } else {
                logger.error("Invalid private key format detected in the resource: {}. Supported formats are PEMKeyPair and PrivateKeyInfo.", resource.getFilename());
                throw new IllegalArgumentException("Invalid key format in resource: " + resource.getFilename());
            }
        } catch (FileNotFoundException e) {
            logger.error("The resource file was not found: {}. Ensure the file exists and the path is correct.", resource.getFilename(), e);
            throw e;
        } catch (IOException e) {
            logger.error("An I/O error occurred while reading the private key from the resource: {}. Error: {}", resource.getFilename(), e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("An unexpected error occurred while loading the private key from the resource: {}. Error: {}", resource.getFilename(), e.getMessage(), e);
            throw e;
        }
    }

    private String readResourceAsString(Resource resource) throws IOException {
        logger.info("Starting to read the content of the resource file: {}", resource.getFilename());
        try (InputStream is = resource.getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            logger.info("Successfully read the content of the resource file: {}. Content size: {} bytes", resource.getFilename(), content.length());
            return content;
        } catch (IOException e) {
            logger.error("Failed to read the content of the resource file: {}. Error: {}", resource.getFilename(), e.getMessage(), e);
            throw e;
        }
    }

    private String convertCerToPem(Resource resource) throws IOException {
        logger.info("Starting the conversion of the CER file to PEM format: {}", resource.getFilename());
        try (InputStream is = resource.getInputStream()) {
            String encoded = Base64.getEncoder().encodeToString(is.readAllBytes());
            String pem = "-----BEGIN CERTIFICATE-----\n" + wrapBase64(encoded) + "\n-----END CERTIFICATE-----";
            logger.info("Successfully converted the CER file to PEM format: {}. PEM size: {} characters", resource.getFilename(), pem.length());
            return pem;
        } catch (IOException e) {
            logger.error("Failed to convert the CER file to PEM format: {}. Error: {}", resource.getFilename(), e.getMessage(), e);
            throw e;
        }
    }

    private String readCertificates() throws IOException {
        logger.info("Starting to read and construct the Apple CA certificates and MDM PEM chain.");

        logger.debug("Reading MDM PEM resource.");
        String mdmPem = readResourceAsString(mdmPemResource);

        logger.debug("Converting Apple WWDR CA G3 certificate to PEM format.");
        String appleWWDRCAG3Pem = convertCerToPem(appleWWDRCAG3Resource);

        logger.debug("Converting Apple Inc. Root Certificate to PEM format.");
        String appleIncRootCertificatePem = convertCerToPem(appleIncRootCertificateResource);

        String certChain = mdmPem + "\n" + appleWWDRCAG3Pem + "\n" + appleIncRootCertificatePem;
        logger.info("Certificate chain successfully constructed.");

        File chainFile = new File(CERTS_DIR + "/chain.pem");
        logger.debug("Writing certificate chain to file: {}", chainFile.getAbsolutePath());
        try (FileWriter fw = new FileWriter(chainFile)) {
            fw.write(certChain);
            logger.info("Certificate chain successfully written to file: {}", chainFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to write certificate chain to file: {}. Error: {}", chainFile.getAbsolutePath(), e.getMessage(), e);
            throw e;
        }

        return certChain;
    }

    private String signData(byte[] data, PrivateKey privateKey) throws Exception {
        logger.info("Starting the process to sign data using the provided private key.");

        logger.debug("Initializing the signature instance with algorithm: SHA256withRSA.");
        Signature signature = Signature.getInstance("SHA256withRSA", BouncyCastleProvider.PROVIDER_NAME);

        logger.debug("Initializing the signature instance with the private key.");
        signature.initSign(privateKey);

        logger.debug("Updating the signature instance with the data to be signed.");
        signature.update(data);

        logger.info("Signing the data.");
        String signedData = Base64.getEncoder().encodeToString(signature.sign());
        logger.info("Data successfully signed and encoded to Base64 format.");

        return signedData;
    }

    @Override
    public void renewVendorCertificate(org.springframework.web.multipart.MultipartFile file, String secretPassword) throws Exception {
        logger.info("Vendor certificate renewal requested.");

        // Validate secret password
        if (secretPassword == null || !secretPassword.equals(vendorRenewalSecret)) {
            logger.error("Vendor certificate renewal failed: Invalid secret password.");
            throw new SecurityException("Invalid secret password for vendor certificate renewal.");
        }

        if (file == null || file.isEmpty()) {
            logger.error("Vendor certificate renewal failed: File is empty.");
            throw new IllegalArgumentException("Vendor certificate file is required.");
        }

        // Validate file extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".pem") && !originalFilename.toLowerCase().endsWith(".cer"))) {
            logger.error("Vendor certificate renewal failed: Invalid file extension.");
            throw new IllegalArgumentException("Vendor certificate must be a .pem or .cer file.");
        }

        // Determine target path - vendor cert is stored in classpath:apple/vendor.cer or mdm.pem
        // For runtime renewal, we store in certs/apple/ directory
        File vendorCertDir = new File(CERTS_DIR);
        if (!vendorCertDir.exists() && !vendorCertDir.mkdirs()) {
            throw new IOException("Failed to create vendor cert directory: " + CERTS_DIR);
        }

        File vendorCertFile = new File(CERTS_DIR + "/vendor.pem");

        // Backup existing file if exists
        if (vendorCertFile.exists()) {
            File backupFile = new File(vendorCertFile.getAbsolutePath() + VENDOR_CERT_BACKUP_SUFFIX + "." + System.currentTimeMillis());
            if (!vendorCertFile.renameTo(backupFile)) {
                logger.warn("Failed to create backup of existing vendor certificate.");
            } else {
                logger.info("Existing vendor certificate backed up to: {}", backupFile.getAbsolutePath());
            }
        }

        // Save new vendor certificate
        try {
            file.transferTo(vendorCertFile);
            logger.info("New vendor certificate saved to: {}", vendorCertFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save new vendor certificate: {}", e.getMessage());
            throw e;
        }

        logger.info("Vendor certificate renewal completed successfully.");
    }
}
