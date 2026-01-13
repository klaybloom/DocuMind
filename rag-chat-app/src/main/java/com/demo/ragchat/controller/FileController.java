package com.demo.ragchat.controller;

import com.demo.ragchat.service.DocumentService;
import com.demo.ragchat.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileController {

    private final DocumentService documentService;
    private final RagService ragService;

    public FileController(DocumentService documentService, RagService ragService) {
        this.documentService = documentService;
        this.ragService = ragService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        String fileName = documentService.storeFile(file);
        // Automatically refresh index on upload
        ragService.refreshIndex();
        return ResponseEntity.ok(Map.of("message", "File uploaded successfully: " + fileName));
    }

    @GetMapping("/list")
    public List<String> listFiles() {
        return documentService.listFiles();
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshIndex() {
        ragService.refreshIndex();
        return ResponseEntity.ok(Map.of("message", "Index refreshed successfully"));
    }

    @DeleteMapping("/{filename}")
    public ResponseEntity<Map<String, String>> deleteFile(@PathVariable String filename) {
        documentService.deleteFile(filename);
        ragService.refreshIndex();
        return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
    }
}
