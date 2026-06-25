package com.documind.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FileUploadResponse {

    private String message;
    private String filename;
    private String error;

    public static FileUploadResponse success(String message, String filename) {
        FileUploadResponse response = new FileUploadResponse();
        response.setMessage(message);
        response.setFilename(filename);
        return response;
    }

    public static FileUploadResponse error(String error) {
        FileUploadResponse response = new FileUploadResponse();
        response.setError(error);
        return response;
    }
}
