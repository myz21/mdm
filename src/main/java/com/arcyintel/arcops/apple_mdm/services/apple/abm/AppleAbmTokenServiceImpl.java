package com.arcyintel.arcops.apple_mdm.services.apple.abm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.arcyintel.arcops.apple_mdm.models.cert.abm.ServerToken;
import com.arcyintel.arcops.apple_mdm.services.apple.abm.AppleAbmTokenService;
import com.arcyintel.arcops.apple_mdm.utils.storage.RenameMultipartFile;
import com.arcyintel.arcops.apple_mdm.utils.storage.StorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;

@Service
@Primary
@RequiredArgsConstructor
public class AppleAbmTokenServiceImpl implements AppleAbmTokenService {

    private static final Logger logger = LoggerFactory.getLogger(AppleAbmTokenServiceImpl.class);
    private static final String CERTS_DIR = "certs/apple";
    private static final String CUSTOMER_PRIVATE_KEY_FILE = "customer.key";
    private static final int RSA_KEY_SIZE = 2048;
    private static final String SIGN_ALGORITHM = "SHA256withRSA";
    private static final long CERT_VALIDITY_MS = 365L * 24 * 60 * 60 * 1000;
    private static final ObjectMapper ABM_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final StorageService storageService;
    @Value("${mdm.cert.subject.commonName}")
    private String commonName;
    @Value("${mdm.cert.paths.directory:certs/apple}")
    private String uploadDirectory;

    @PostConstruct
    public void init() {
        try {
            logger.info("Customer private key successfully loaded from disk.");
        } catch (Exception e) {
            logger.error("Failed to load the customer private key from disk. Error: {}", e.getMessage(), e);
        }
    }


    @Override
    public void uploadServerToken(MultipartFile file) throws Exception {
        logger.info("Starting the process to upload and process the server token file.");

        if (file.isEmpty()) {
            logger.error("The provided server token file is empty. Aborting the upload process.");
            throw new IOException("The provided server token file is empty.");
        }

        logger.info("Uploading server token file with original name: {}", file.getOriginalFilename());
        MultipartFile renamedFile = new RenameMultipartFile(file, "server_token.p7m");
        storageService.store(renamedFile, "apple");
        logger.info("Server token file '{}' successfully uploaded and stored in the 'apple' directory.", renamedFile.getOriginalFilename());

        logger.info("Parsing the uploaded server token file to extract its contents.");
        ServerToken serverToken = parseP7M(file);
        logger.info("Server token successfully parsed. Details: {}", serverToken);

        logger.info("Saving the parsed server token as a JSON file for future use.");
        saveServerTokenAsJson(serverToken);
        logger.info("Server token successfully saved as a JSON file.");

        logger.info("Server token upload and processing completed successfully.");
    }

    @Override
    public void uploadVppToken(MultipartFile file) throws Exception {
        logger.info("Starting the process to upload and store the VPP token file.");

        if (file.isEmpty()) {
            logger.error("The provided VPP token file is empty. Aborting the upload process.");
            throw new IOException("The provided VPP token file is empty.");
        }

        logger.info("Uploading VPP token file with original name: {}", file.getOriginalFilename());
        MultipartFile renamedFile = new RenameMultipartFile(file, "vpp_token.vpp");
        storageService.store(renamedFile, "apple");
        logger.info("VPP token file '{}' successfully uploaded and stored in the 'apple' directory.", renamedFile.getOriginalFilename());

        // Optionally: parse token for logging or validation
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        logger.info("VPP Token content preview (sanitized): {}", content.substring(0, Math.min(300, content.length())).replaceAll("\\s", " "));

        // You can extend this method to validate or parse the JSON further if needed
    }

    @Override
    public String generateDepCertificateAndSave() throws Exception {

        logger.info("Starting the process to generate the DEP certificate.");

        logger.info("Generating a new RSA key pair for the DEP certificate.");
        KeyPair keyPair = generateKeyPair();
        savePrivateKeyAsPEM(keyPair.getPrivate());
        logger.info("DEP private key successfully generated and saved as PEM.");

        logger.info("Creating a self-signed X.509 certificate for the DEP certificate.");
        X509Certificate certificate = generateCertificate(keyPair);
        saveCertificateAsDER(certificate);
        logger.info("DEP certificate successfully generated and saved as DER format.");

        logger.info("Converting the DEP certificate to PEM format for output.");
        String pemCertificate = convertCertificateToPEM(certificate);
        logger.info("DEP certificate successfully converted to PEM format.");

        logger.info("DEP certificate generation process completed successfully.");
        return pemCertificate;
    }

    private ServerToken parseP7M(MultipartFile file) throws Exception {

        logger.info("Starting the process to parse the server token from the uploaded file: {}", file.getOriginalFilename());

        logger.debug("Extracting raw CMS data from the uploaded file.");
        byte[] cmsData = unwrapSMIME(file.getBytes());
        logger.debug("Raw CMS data successfully extracted from the file.");

        logger.info("Loading the DEP private key from the storage service.");
        PrivateKey depPrivateKey = loadPrivateKey(storageService.loadAsResource("dep.key", "apple"));
        logger.info("DEP private key successfully loaded.");

        logger.info("Initializing CMS Enveloped Data for decryption.");
        CMSEnvelopedData envelopedData = new CMSEnvelopedData(cmsData);

        logger.debug("Extracting recipient information from the CMS Enveloped Data.");
        RecipientInformation recipient = envelopedData.getRecipientInfos().getRecipients().iterator().next();
        logger.info("Recipient information successfully extracted.");

        logger.info("Decrypting the server token data using the DEP private key.");
        byte[] decryptedData = recipient.getContent(new JceKeyTransEnvelopedRecipient(depPrivateKey).setProvider("BC"));
        logger.info("Server token data successfully decrypted.");

        logger.info("Parsing the decrypted server token data into a ServerToken object.");
        ServerToken serverToken = ABM_MAPPER.readValue(unwrapTokenJson(decryptedData), ServerToken.class);
        logger.info("Server token successfully parsed. Token details: {}", serverToken);

        return serverToken;
    }

    private byte[] unwrapSMIME(byte[] smime) {
        logger.info("Starting the process to unwrap S/MIME data.");

        if (smime == null || smime.length == 0) {
            logger.error("Provided S/MIME data is null or empty. Cannot proceed with unwrapping. Input length: {}", smime == null ? "null" : smime.length);
            throw new IllegalArgumentException("S/MIME data cannot be null or empty.");
        }

        logger.debug("Decoding Base64-encoded S/MIME data. Input size: {}", smime.length);
        String smimeContent = new String(smime, StandardCharsets.ISO_8859_1);
        logger.debug("Decoded S/MIME content to string. Content length: {}", smimeContent.length());

        String[] parts = smimeContent.split("\\r?\\n\\r?\\n", 2);
        if (parts.length < 2) {
            logger.error("Invalid S/MIME format. Headers and body could not be separated. Content: {}", smimeContent);
            throw new IllegalArgumentException("Invalid S/MIME format. Missing headers or body.");
        }

        String body = parts[1].replaceAll("\\s", "");
        logger.debug("Successfully extracted and sanitized the body of the S/MIME data. Body length: {}", body.length());

        byte[] decodedData;
        try {
            decodedData = Base64.getDecoder().decode(body);
            logger.info("S/MIME data successfully unwrapped. Decoded data size: {}", decodedData.length);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to decode Base64 content of the S/MIME body. Body content: {}", body, e);
            throw e;
        }

        return decodedData;
    }

    private byte[] unwrapTokenJson(byte[] wrapped) throws IOException {

        logger.debug("Starting the process to unwrap the token JSON data.");

        if (wrapped == null || wrapped.length == 0) {
            logger.error("The provided wrapped token data is null or empty. Cannot proceed with unwrapping.");
            throw new IllegalArgumentException("Wrapped token data cannot be null or empty.");
        }

        String content = new String(wrapped, StandardCharsets.UTF_8);
        logger.debug("Wrapped token data successfully converted to string. Content length: {}", content.length());

        logger.debug("Sanitizing the content to separate headers and body.");
        String[] parts = content.split("\\r?\\n\\r?\\n", 2);
        if (parts.length < 2) {
            logger.error("Invalid wrapped content format. Headers and body could not be separated.");
            throw new IOException("Invalid wrapped content, cannot split headers and body.");
        }

        String body = parts[1];
        logger.debug("Body of the wrapped content successfully extracted. Body length: {}", body.length());

        logger.debug("Searching for JSON part within the body using BEGIN and END markers.");
        int beginIndex = body.indexOf("-----BEGIN MESSAGE-----");
        int endIndex = body.indexOf("-----END MESSAGE-----");

        if (beginIndex == -1 || endIndex == -1 || beginIndex >= endIndex) {
            logger.error("Invalid wrapped content format. BEGIN or END markers not found or improperly placed.");
            throw new IOException("Invalid wrapped content, BEGIN or END markers not found properly.");
        }

        logger.debug("Extracting the JSON part from the body.");
        String jsonPart = body.substring(beginIndex + "-----BEGIN MESSAGE-----".length(), endIndex).trim();

        if (jsonPart.isEmpty()) {
            logger.error("Extracted JSON part is empty. The wrapped content might be corrupted.");
            throw new IOException("Extracted JSON part is empty.");
        }

        logger.info("JSON part successfully extracted from the wrapped content. JSON length: {}", jsonPart.length());
        return jsonPart.getBytes(StandardCharsets.UTF_8);
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

    private void saveServerTokenAsJson(ServerToken serverToken) throws IOException {
        logger.info("Starting the process to save the server token as a JSON file.");
        File jsonFile = new File(uploadDirectory, "server_token.json");
        logger.debug("Resolved JSON file path: {}", jsonFile.getAbsolutePath());

        try {
            ABM_MAPPER.writeValue(jsonFile, serverToken);
            logger.info("Server token successfully saved to JSON file at: {}", jsonFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save the server token to JSON file at: {}. Error: {}", jsonFile.getAbsolutePath(), e.getMessage(), e);
            throw e;
        }
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

    private void savePrivateKeyAsPEM(PrivateKey privateKey) throws IOException {
        logger.info("Starting the process to save the private key as a PEM file.");
        logger.debug("Target file path for saving private key: {}", AppleAbmTokenService.DEP_KEY_FILE);

        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(AppleAbmTokenService.DEP_KEY_FILE))) {
            writer.writeObject(privateKey);
            logger.info("Private key successfully saved as a PEM file at: {}", AppleAbmTokenService.DEP_KEY_FILE);
        } catch (IOException e) {
            logger.error("Failed to save the private key as a PEM file at: {}. Error: {}", AppleAbmTokenService.DEP_KEY_FILE, e.getMessage(), e);
            throw e;
        }
    }

    private X509Certificate generateCertificate(KeyPair keyPair) throws Exception {
        logger.info("Starting the process to generate a self-signed X.509 certificate.");

        logger.debug("Creating X500Name subject with common name: {}", commonName);
        X500Name subject = new X500Name("CN=" + commonName);

        logger.info("Initializing content signer with the private key from the provided key pair.");
        ContentSigner signer = new JcaContentSignerBuilder(SIGN_ALGORITHM).build(keyPair.getPrivate());
        logger.debug("Content signer successfully initialized with algorithm: {}", SIGN_ALGORITHM);

        logger.info("Building the X.509 certificate with the provided subject and public key.");
        X509CertificateHolder certHolder = new JcaX509v3CertificateBuilder(
                subject, new BigInteger(128, new SecureRandom()),
                new Date(), new Date(System.currentTimeMillis() + CERT_VALIDITY_MS),
                subject, keyPair.getPublic()
        ).build(signer);
        logger.debug("X.509 certificate successfully built.");

        logger.info("Converting the X.509 certificate holder to an X509Certificate object.");
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider())
                .getCertificate(certHolder);
        logger.info("Self-signed X.509 certificate generation completed successfully.");

        return certificate;
    }

    private void saveCertificateAsDER(X509Certificate certificate) throws Exception {
        logger.info("Starting the process to save the certificate as a DER file.");
        logger.debug("Target file path for saving certificate: {}", AppleAbmTokenService.DEP_DER_FILE);

        try {
            Files.write(Paths.get(AppleAbmTokenService.DEP_DER_FILE), certificate.getEncoded());
            logger.info("Certificate successfully saved as a DER file at: {}", AppleAbmTokenService.DEP_DER_FILE);
        } catch (IOException e) {
            logger.error("Failed to save the certificate as a DER file at: {}. Error: {}", AppleAbmTokenService.DEP_DER_FILE, e.getMessage(), e);
            throw e;
        }
    }

    private String convertCertificateToPEM(X509Certificate certificate) throws IOException {

        logger.info("Starting the process to convert the certificate to PEM format.");
        StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            logger.debug("Writing the certificate object to PEM format.");
            pemWriter.writeObject(certificate);
        } catch (IOException e) {
            logger.error("An error occurred while converting the certificate to PEM format. Error: {}", e.getMessage(), e);
            throw e;
        }
        String pem = stringWriter.toString();
        logger.info("Certificate successfully converted to PEM format.");
        logger.debug("Converted PEM format: {}", pem);
        return pem;
    }
}
