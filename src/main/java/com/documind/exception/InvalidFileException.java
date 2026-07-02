package com.documind.exception;

/**
 * 上传文件不合法时抛出的业务异常。
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}
