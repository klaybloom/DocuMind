package com.demo.ragchat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    @Value("${app.documents-path}")
    private String documentsPath;

    public void init() {
        try {
            Files.createDirectories(Paths.get(documentsPath));
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    public String storeFile(MultipartFile file) {
        try {
            init();
            Path targetLocation = Paths.get(documentsPath).resolve(file.getOriginalFilename());
            Files.copy(file.getInputStream(), targetLocation, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return file.getOriginalFilename();
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + file.getOriginalFilename(), ex);
        }
    }

    public List<String> listFiles() {
        try {
            init();
            return Files.list(Paths.get(documentsPath))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public void deleteFile(String filename) {
        try {
            Files.deleteIfExists(Paths.get(documentsPath).resolve(filename));
        } catch (IOException e) {
            throw new RuntimeException("Could not delete file " + filename, e);
        }
    }
}
