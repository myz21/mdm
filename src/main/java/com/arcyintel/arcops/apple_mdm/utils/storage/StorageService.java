package com.arcyintel.arcops.apple_mdm.utils.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Path;
import java.util.stream.Stream;

public interface StorageService {

    void store(MultipartFile file, String subDirectory);

    Path load(String filename, String subDirectory);

    Resource loadAsResource(String filename, String subDirectory);

    Stream<Path> loadAll(String subDirectory);

    void store(File file, String subDirectory);
}