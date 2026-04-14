package com.arcyintel.arcops.apple_mdm.services.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class TempAppStorage {

    private static final Logger logger = LoggerFactory.getLogger(TempAppStorage.class);
    private static final Path TEMP_ROOT = Paths.get("apps/enterprise/temp");
    private static final Duration MAX_AGE = Duration.ofHours(1);

    public String store(MultipartFile file) throws IOException {
        String tempId = UUID.randomUUID().toString();
        Path dir = TEMP_ROOT.resolve(tempId);
        Files.createDirectories(dir);
        String safeName = sanitizeFilename(file.getOriginalFilename());
        Path target = dir.resolve(safeName);
        file.transferTo(target.toFile());
        logger.info("Stored temp app: tempId={}, file={}", tempId, safeName);
        return tempId;
    }

    public Path getFile(String tempId) throws IOException {
        Path dir = TEMP_ROOT.resolve(sanitizeTempId(tempId));
        if (!Files.isDirectory(dir)) {
            throw new IOException("Temp directory not found: " + tempId);
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith(".ipa") || name.endsWith(".pkg");
                    })
                    .findFirst()
                    .orElseThrow(() -> new IOException("No app file found in temp dir: " + tempId));
        }
    }

    public String getFilename(String tempId) throws IOException {
        return getFile(tempId).getFileName().toString();
    }

    public void delete(String tempId) {
        Path dir = TEMP_ROOT.resolve(sanitizeTempId(tempId));
        deleteDirectoryQuietly(dir);
        logger.info("Deleted temp app: tempId={}", tempId);
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanupOldTemp() {
        if (!Files.isDirectory(TEMP_ROOT)) return;
        try (Stream<Path> dirs = Files.list(TEMP_ROOT)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(dir, BasicFileAttributes.class);
                    Instant created = attrs.creationTime().toInstant();
                    if (Instant.now().minus(MAX_AGE).isAfter(created)) {
                        deleteDirectoryQuietly(dir);
                        logger.info("Cleaned up old temp dir: {}", dir.getFileName());
                    }
                } catch (IOException e) {
                    logger.warn("Failed to check temp dir age: {}", dir, e);
                }
            });
        } catch (IOException e) {
            logger.warn("Failed to list temp root for cleanup: {}", e.getMessage());
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "upload.bin";
        return Path.of(filename).getFileName().toString();
    }

    private String sanitizeTempId(String tempId) {
        return Path.of(tempId).getFileName().toString();
    }

    private void deleteDirectoryQuietly(Path dir) {
        if (!Files.exists(dir)) return;
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.warn("Failed to delete directory {}: {}", dir, e.getMessage());
        }
    }
}
