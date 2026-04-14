package com.arcyintel.arcops.apple_mdm.utils.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class RenameMultipartFile implements MultipartFile {

    private final MultipartFile original;
    private final String newFilename;

    public RenameMultipartFile(MultipartFile original, String newFilename) {
        this.original = original;
        this.newFilename = newFilename;
    }

    @Override
    public String getName() {
        return newFilename;
    }

    @Override
    public String getOriginalFilename() {
        return newFilename;
    }

    @Override
    public String getContentType() {
        return original.getContentType();
    }

    @Override
    public boolean isEmpty() {
        return original.isEmpty();
    }

    @Override
    public long getSize() {
        return original.getSize();
    }

    @Override
    public byte[] getBytes() throws IOException {
        return original.getBytes();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return original.getInputStream();
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        original.transferTo(dest);
    }
}