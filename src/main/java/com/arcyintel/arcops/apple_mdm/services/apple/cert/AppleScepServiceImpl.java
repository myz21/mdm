package com.arcyintel.arcops.apple_mdm.services.apple.cert;

import com.arcyintel.arcops.apple_mdm.services.apple.cert.AppleScepService;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AppleScepServiceImpl implements AppleScepService {

    private static final ASN1ObjectIdentifier OID_MESSAGE_TYPE =
            new ASN1ObjectIdentifier("2.16.840.1.113733.1.9.2");
    private static final ASN1ObjectIdentifier OID_PKI_STATUS =
            new ASN1ObjectIdentifier("2.16.840.1.113733.1.9.3");
    private static final ASN1ObjectIdentifier OID_FAIL_INFO =
            new ASN1ObjectIdentifier("2.16.840.1.113733.1.9.4");
    private static final ASN1ObjectIdentifier OID_SENDER_NONCE =
            new ASN1ObjectIdentifier("2.16.840.1.113733.1.9.5");
    private static final ASN1ObjectIdentifier OID_RECIPIENT_NONCE =
            new ASN1ObjectIdentifier("2.16.840.1.113733.1.9.6");
    private static final ASN1ObjectIdentifier OID_TRANSACTION_ID =
            new ASN1ObjectIdentifier("2.16.840.1.113733.1.9.7");
    private final KeyPair caKeyPair;
    private final X509Certificate caCertificate;

    /**
     * <p>
     * defaultCaps := []byte("Renewal\nSHA-1\nSHA-256\nAES\nDES3\nSCEPStandard\nPOSTPKIOperation")
     * <p>
     * ile bire bir aynı çıktı.
     */
    public String getCaCaps() {
        return """
                Renewal
                SHA-1
                SHA-256
                AES
                DES3
                SCEPStandard
                POSTPKIOperation
                """;
    }

    /**
     * GetCACert (degenerate=false) ile aynı mantık:
     * tek CA sertifikası raw DER dönüyoruz.
     */
    public byte[] getCaCertificateBytes() throws Exception {
        return caCertificate.getEncoded();
    }

    /**
     * PKIOperation handler:
     * - Go’daki ParsePKIMessage + DecryptPKIEnvelope + SignCSR + Success ile aynı işlev.
     */
    public byte[] handlePkiOperation(byte[] messageBytes) throws Exception {

        // 1) Incoming PKIMessage → SignedData parse et
        CMSSignedData signedData = new CMSSignedData(messageBytes);

        // 1.a) Signer + signed attributes
        SignerInformationStore signerInfos = signedData.getSignerInfos();
        Iterator<SignerInformation> it = signerInfos.getSigners().iterator();
        if (!it.hasNext()) {
            throw new CMSException("No signer in SCEP request");
        }
        SignerInformation signerInfo = it.next();
        AttributeTable signedAttrs = signerInfo.getSignedAttributes();
        if (signedAttrs == null) {
            throw new CMSException("No signed attributes in SCEP request");
        }

        // 1.b) transactionID
        Attribute transIdAttr = signedAttrs.get(OID_TRANSACTION_ID);
        if (transIdAttr == null) {
            throw new CMSException("Missing transactionID in SCEP request");
        }
        String transactionId =
                ((ASN1String) transIdAttr.getAttrValues().getObjectAt(0)).getString();

        // 1.c) senderNonce
        Attribute senderNonceAttr = signedAttrs.get(OID_SENDER_NONCE);
        if (senderNonceAttr == null) {
            throw new CMSException("Missing senderNonce in SCEP request");
        }
        ASN1OctetString senderNonceReq =
                (ASN1OctetString) senderNonceAttr.getAttrValues().getObjectAt(0);
        byte[] senderNonceReqBytes = senderNonceReq.getOctets();

        @SuppressWarnings("unchecked")
        Collection<X509CertificateHolder> reqCertHolders =
                signedData.getCertificates().getMatches(null);

        List<X509Certificate> recipientCerts = new ArrayList<>();
        JcaX509CertificateConverter converter = new JcaX509CertificateConverter().setProvider("BC");
        for (X509CertificateHolder holder : reqCertHolders) {
            recipientCerts.add(converter.getCertificate(holder));
        }

        // 2) SignedData content = EnvelopedData (csr şifreli)
        CMSProcessable signedContent = signedData.getSignedContent();
        byte[] envelopedBytes = (byte[]) signedContent.getContent();

        // 3) EnvelopedData’yı decrypt et → CSR
        CMSEnvelopedData enveloped = new CMSEnvelopedData(envelopedBytes);
        RecipientInformationStore recipients = enveloped.getRecipientInfos();
        RecipientInformation recipient =
                recipients.getRecipients().iterator().next();

        byte[] csrBytes = recipient.getContent(
                new JceKeyTransEnvelopedRecipient(caKeyPair.getPrivate()).setProvider("BC")
        );

        JcaPKCS10CertificationRequest csr = new JcaPKCS10CertificationRequest(csrBytes);

        X500Name subject = csr.getSubject();
        PublicKey clientPublicKey = csr.getPublicKey();

        X509Certificate clientCert = generateCertificate(subject, clientPublicKey);

        return createScepResponse(clientCert, transactionId, senderNonceReqBytes, recipientCerts);
    }

    /**
     * Go tarafındaki SignCSR’e denk gelen fonksiyonumuz.
     */
    private X509Certificate generateCertificate(X500Name subject, PublicKey clientKey) throws Exception {
        long now = System.currentTimeMillis();

        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
                new X500Name("CN=ArcOps-CA"),
                new BigInteger(Long.toString(now)),
                new Date(now),
                new Date(now + 365L * 24 * 60 * 60 * 1000L),
                subject,
                SubjectPublicKeyInfo.getInstance(clientKey.getEncoded())
        );

        ContentSigner signer =
                new JcaContentSignerBuilder("SHA256WithRSA")
                        .setProvider("BC")
                        .build(caKeyPair.getPrivate());

        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(builder.build(signer));
    }

    /**
     * <p>
     * - Issued cert → degenerate PKCS#7
     * - degenerate → EnvelopedData (msg.p7.Certificates ile encrypt)
     * - EnvelopedData → SignedData content
     * - SignedAttributes: messageType=3 (CertRep), pkiStatus=0 (SUCCESS),
     * senderNonce, recipientNonce, transactionID
     */
    private byte[] createScepResponse(X509Certificate issuedCert,
                                      String transactionId,
                                      byte[] senderNonceReqBytes,
                                      List<X509Certificate> recipientCerts) throws Exception {

        // --------------------------
        // 1) Issued cert → degenerate PKCS#7
        // --------------------------
        CMSSignedDataGenerator degGen = new CMSSignedDataGenerator();
        degGen.addCertificate(new X509CertificateHolder(issuedCert.getEncoded()));

        CMSSignedData degenerate = degGen.generate(new CMSAbsentContent(), false);
        byte[] degBytes = degenerate.getEncoded();

        // --------------------------
        // 2) EnvelopedData (Go: pkcs7.Encrypt(deg, msg.p7.Certificates))
        // --------------------------
        CMSEnvelopedDataGenerator envGen = new CMSEnvelopedDataGenerator();
        JcaX509CertificateConverter converter = new JcaX509CertificateConverter().setProvider("BC");

        if (recipientCerts.isEmpty()) {
            // fallback: CA sertifikasını kullan
            envGen.addRecipientInfoGenerator(
                    new JceKeyTransRecipientInfoGenerator(caCertificate).setProvider("BC"));
        } else {
            for (X509Certificate rcpt : recipientCerts) {
                envGen.addRecipientInfoGenerator(
                        new JceKeyTransRecipientInfoGenerator(rcpt).setProvider("BC"));
            }
        }

        OutputEncryptor encryptor = new JceCMSContentEncryptorBuilder(CMSAlgorithm.DES_EDE3_CBC)
                .setProvider("BC")
                .build();

        CMSEnvelopedData enveloped = envGen.generate(new CMSProcessableByteArray(degBytes), encryptor);
        byte[] envelopedBytes = enveloped.getEncoded();

        // --------------------------
        // 3) SignedAttributes (Go Success())
        // --------------------------
        ASN1EncodableVector attrs = new ASN1EncodableVector();

        attrs.add(new Attribute(OID_TRANSACTION_ID,
                new DERSet(new DERPrintableString(transactionId))));

        attrs.add(new Attribute(OID_MESSAGE_TYPE,
                new DERSet(new DERPrintableString("3")))); // CertRep

        attrs.add(new Attribute(OID_PKI_STATUS,
                new DERSet(new DERPrintableString("0")))); // SUCCESS

        // senderNonce = client.senderNonce
        attrs.add(new Attribute(OID_SENDER_NONCE,
                new DERSet(new DEROctetString(senderNonceReqBytes))));

        // recipientNonce = client.senderNonce
        attrs.add(new Attribute(OID_RECIPIENT_NONCE,
                new DERSet(new DEROctetString(senderNonceReqBytes))));

        AttributeTable signedAttrTable = new AttributeTable(attrs);

        // --------------------------
        // 4) SignedData generator
        // --------------------------
        CMSSignedDataGenerator signedGen = new CMSSignedDataGenerator();

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider("BC")
                .build(caKeyPair.getPrivate());

        JcaSignerInfoGeneratorBuilder sigBuilder =
                new JcaSignerInfoGeneratorBuilder(
                        new JcaDigestCalculatorProviderBuilder()
                                .setProvider("BC")
                                .build()
                );

        sigBuilder.setSignedAttributeGenerator(
                new DefaultSignedAttributeTableGenerator(signedAttrTable));

        signedGen.addSignerInfoGenerator(sigBuilder.build(signer, caCertificate));

        // client cert + CA cert ekle
        signedGen.addCertificate(new X509CertificateHolder(issuedCert.getEncoded()));
        signedGen.addCertificate(new X509CertificateHolder(caCertificate.getEncoded()));

        // --------------------------
        // 5) Final PKCS#7 SignedData üret
        // --------------------------
        CMSProcessableByteArray content = new CMSProcessableByteArray(envelopedBytes);
        CMSSignedData finalSigned = signedGen.generate(content, true);

        return finalSigned.getEncoded();
    }
}
