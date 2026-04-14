package com.arcyintel.arcops.apple_mdm.utils.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class FileSystemStorageService implements StorageService {

    private final static Logger logger = LoggerFactory.getLogger(FileSystemStorageService.class);
    private final Path rootLocation = Paths.get("certs");

    public void store(File file, String subDirectory) {
        logger.info("Starting file storage process for subdirectory: {}", subDirectory);
        try {
            if (file == null || !file.exists()) {
                logger.warn("Attempted to store a null or non-existent file in subdirectory: {}", subDirectory);
                throw new StorageException("Failed to store null or non-existent file.");
            }
            Path subDirPath = this.rootLocation.resolve(subDirectory);
            Files.createDirectories(subDirPath);  // Create subdirectory if it doesn't exist.
            logger.debug("Subdirectory created or already exists: {}", subDirPath);

            Path destinationFile = subDirPath.resolve(
                            Paths.get(file.getName()))
                    .normalize().toAbsolutePath();

            if (!destinationFile.startsWith(this.rootLocation.toAbsolutePath())) {
                logger.error("Security violation: Attempt to store file outside root directory. Destination: {}", destinationFile);
                throw new StorageException("Cannot store file outside root directory.");
            }

            try (InputStream inputStream = Files.newInputStream(file.toPath())) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
                logger.info("File successfully stored at: {}", destinationFile);
            }
        } catch (IOException e) {
            logger.error("Failed to store file in subdirectory: {}. Error: {}", subDirectory, e.getMessage());
            throw new StorageException("Failed to store file.", e);
        }
    }


    public void store(MultipartFile file, String subDirectory) {
        logger.info("Starting file storage process for subdirectory: {}", subDirectory);
        try {
            if (file.isEmpty()) {
                logger.warn("Attempted to store an empty file in subdirectory: {}", subDirectory);
                throw new StorageException("Failed to store empty file.");
            }
            Path subDirPath = this.rootLocation.resolve(subDirectory);
            Files.createDirectories(subDirPath);  // Create subdirectory if it doesn't exist.
            logger.debug("Subdirectory created or already exists: {}", subDirPath);

            Path destinationFile = subDirPath.resolve(
                            Paths.get(Objects.requireNonNull(file.getOriginalFilename())))
                    .normalize().toAbsolutePath();

            if (!destinationFile.startsWith(this.rootLocation.toAbsolutePath())) {
                logger.error("Security violation: Attempt to store file outside root directory. Destination: {}", destinationFile);
                throw new StorageException("Cannot store file outside root directory.");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
                logger.info("File successfully stored at: {}", destinationFile);
            }
        } catch (IOException e) {
            logger.error("Failed to store file in subdirectory: {}. Error: {}", subDirectory, e.getMessage());
            throw new StorageException("Failed to store file.", e);
        }
    }

    @Override
    public Path load(String filename, String subDirectory) {
        logger.info("Loading file '{}' from subdirectory '{}'", filename, subDirectory);
        Path filePath = rootLocation.resolve(subDirectory).resolve(filename);
        logger.debug("Resolved file path: {}", filePath);
        return filePath;
    }

    @Override
    public Resource loadAsResource(String filename, String subDirectory) {
        logger.info("Loading file '{}' as resource from subdirectory '{}'", filename, subDirectory);
        try {
            Path file = load(filename, subDirectory);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                logger.info("File '{}' successfully loaded as resource from '{}'", filename, file);
                return resource;
            } else {
                logger.warn("File '{}' could not be read or does not exist in '{}'", filename, file);
                throw new StorageFileNotFoundException("Could not read file: " + filename);
            }
        } catch (MalformedURLException e) {
            logger.error("Malformed URL while loading file '{}' as resource from subdirectory '{}'. Error: {}", filename, subDirectory, e.getMessage());
            throw new StorageFileNotFoundException("Could not read file: " + filename, e);
        }
    }

    @Override
    public Stream<Path> loadAll(String subDirectory) {
        logger.info("Loading all files from subdirectory '{}'", subDirectory);
        try {
            Path subDirPath = rootLocation.resolve(subDirectory);
            logger.debug("Resolved subdirectory path: {}", subDirPath);
            Stream<Path> files = Files.walk(subDirPath, 1)
                    .filter(path -> !path.equals(subDirPath))
                    .map(subDirPath::relativize);
            logger.info("Successfully loaded all files from subdirectory '{}'", subDirectory);
            return files;
        } catch (IOException e) {
            logger.error("Failed to load files from subdirectory '{}'. Error: {}", subDirectory, e.getMessage());
            throw new StorageException("Failed to read stored files", e);
        }
    }
}