package com.documind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动入口，负责引导 Spring Boot 加载 DocuMind 的后端服务。
 */
@SpringBootApplication
public class DocuMindApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocuMindApplication.class, args);
    }
}
