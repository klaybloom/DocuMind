package com.demo.ragchat.dto;

public class FileUploadResponse {

    private String message;
    private String filename;
    private String error;

    public FileUploadResponse() {
    }

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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
