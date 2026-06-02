package com.demo.ragchat.service;

import com.demo.ragchat.exception.FileStorageException;
import com.demo.ragchat.exception.InvalidFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("pdf", "txt");
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    @Value("${app.documents-path}")
    private String documentsPath;

    public void init() {
        try {
            Path path = Paths.get(documentsPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("Created documents directory: {}", documentsPath);
            }
        } catch (IOException e) {
            logger.error("Failed to initialize storage directory: {}", documentsPath, e);
            throw new FileStorageException("无法初始化存储目录", e);
        }
    }

    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("文件不能为空");
        }

        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException("文件大小不能超过 50MB");
        }

        // Sanitize and validate filename
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new InvalidFileException("文件名无效");
        }

        // Prevent path traversal attacks
        String sanitizedFilename = Paths.get(originalFilename).getFileName().toString();

        // Validate file extension
        String extension = getFileExtension(sanitizedFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new InvalidFileException("不支持的文件类型，仅支持: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        try {
            init();
            Path targetLocation = Paths.get(documentsPath).resolve(sanitizedFilename);

            // Check if file already exists
            if (Files.exists(targetLocation)) {
                logger.warn("File already exists, will be replaced: {}", sanitizedFilename);
            }

            Files.copy(file.getInputStream(), targetLocation, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            logger.info("File stored successfully: {}", sanitizedFilename);
            return sanitizedFilename;
        } catch (IOException ex) {
            logger.error("Failed to store file: {}", sanitizedFilename, ex);
            throw new FileStorageException("无法存储文件: " + sanitizedFilename, ex);
        }
    }

    public List<String> listFiles() {
        try {
            init();
            List<String> files = Files.list(Paths.get(documentsPath))
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .filter(name -> ALLOWED_EXTENSIONS.contains(getFileExtension(name).toLowerCase()))
                    .collect(Collectors.toList());
            logger.debug("Listed {} files from documents directory", files.size());
            return files;
        } catch (IOException e) {
            logger.error("Failed to list files from directory: {}", documentsPath, e);
            return new ArrayList<>();
        }
    }

    public void deleteFile(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new InvalidFileException("文件名不能为空");
        }

        // Prevent path traversal
        String sanitizedFilename = Paths.get(filename).getFileName().toString();

        try {
            Path filePath = Paths.get(documentsPath).resolve(sanitizedFilename);
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                logger.info("File deleted successfully: {}", sanitizedFilename);
            } else {
                logger.warn("File not found for deletion: {}", sanitizedFilename);
            }
        } catch (IOException e) {
            logger.error("Failed to delete file: {}", sanitizedFilename, e);
            throw new FileStorageException("无法删除文件: " + sanitizedFilename, e);
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1);
        }
        return "";
    }
}
