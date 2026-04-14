package com.arcyintel.arcops.apple_mdm.services.apple.cert;

public interface AppleScepService {

    String getCaCaps();


    byte[] getCaCertificateBytes() throws Exception;


    byte[] handlePkiOperation(byte[] messageBytes) throws Exception;
}