package com.demo.ragchat.exception;

import com.demo.ragchat.dto.ChatResponse;
import com.demo.ragchat.dto.FileUploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ChatResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        StringBuilder errorMessage = new StringBuilder();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errorMessage.append(error.getDefaultMessage()).append("; ");
        }
        logger.warn("Validation error: {}", errorMessage);
        return ResponseEntity.badRequest().body(ChatResponse.error(errorMessage.toString()));
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<FileUploadResponse> handleInvalidFileException(InvalidFileException ex) {
        logger.warn("Invalid file: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(FileUploadResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<FileUploadResponse> handleFileStorageException(FileStorageException ex) {
        logger.error("File storage error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileUploadResponse.error("文件存储失败"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<FileUploadResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        logger.warn("File size exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(FileUploadResponse.error("文件大小超过限制（最大 50MB）"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "服务器内部错误，请稍后重试"));
    }
}
