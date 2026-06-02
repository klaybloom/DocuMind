package com.demo.ragchat.controller;

import com.demo.ragchat.dto.FileUploadResponse;
import com.demo.ragchat.exception.FileStorageException;
import com.demo.ragchat.exception.InvalidFileException;
import com.demo.ragchat.service.DocumentService;
import com.demo.ragchat.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    private final DocumentService documentService;
    private final RagService ragService;

    public FileController(DocumentService documentService, RagService ragService) {
        this.documentService = documentService;
        this.ragService = ragService;
    }

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            logger.info("Uploading file: {}", file.getOriginalFilename());
            String fileName = documentService.storeFile(file);
            ragService.refreshIndex();
            return ResponseEntity.ok(FileUploadResponse.success("文件上传成功", fileName));
        } catch (InvalidFileException e) {
            logger.warn("Invalid file upload attempt: {}", e.getMessage());
            return ResponseEntity.badRequest().body(FileUploadResponse.error(e.getMessage()));
        } catch (FileStorageException e) {
            logger.error("File storage error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(FileUploadResponse.error("文件存储失败"));
        } catch (Exception e) {
            logger.error("Unexpected error during file upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(FileUploadResponse.error("上传失败，请稍后重试"));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<String>> listFiles() {
        try {
            List<String> files = documentService.listFiles();
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            logger.error("Error listing files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshIndex() {
        try {
            logger.info("Refreshing index");
            ragService.refreshIndex();
            return ResponseEntity.ok(Map.of("message", "索引刷新成功"));
        } catch (Exception e) {
            logger.error("Error refreshing index", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "索引刷新失败"));
        }
    }

    @DeleteMapping("/{filename}")
    public ResponseEntity<Map<String, String>> deleteFile(@PathVariable String filename) {
        try {
            logger.info("Deleting file: {}", filename);
            documentService.deleteFile(filename);
            ragService.refreshIndex();
            return ResponseEntity.ok(Map.of("message", "文件删除成功"));
        } catch (InvalidFileException e) {
            logger.warn("Invalid file deletion attempt: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (FileStorageException e) {
            logger.error("File deletion error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "文件删除失败"));
        } catch (Exception e) {
            logger.error("Unexpected error during file deletion", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "删除失败，请稍后重试"));
        }
    }
}
