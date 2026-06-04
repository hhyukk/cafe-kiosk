package com.cafekiosk.file.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class FileUploadController {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${server.base-url:http://localhost:8080}")
    private String baseUrl;

    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                log.warn("빈 파일 업로드 시도");
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "파일이 비어있습니다"));
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                log.warn("이미지가 아닌 파일 업로드 시도: {}", contentType);
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "이미지 파일만 업로드 가능합니다"));
            }

            long maxSize = 5 * 1024 * 1024; // 5MB
            if (file.getSize() > maxSize) {
                log.warn("파일 크기 초과: {} bytes", file.getSize());
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "파일 크기는 5MB를 초과할 수 없습니다"));
            }

            Path uploadPath = Paths.get(uploadDir).isAbsolute()
                    ? Paths.get(uploadDir)
                    : Paths.get(System.getProperty("user.dir"), uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("업로드 디렉토리 생성: {}", uploadPath.toAbsolutePath());
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + extension;

            Path filePath = uploadPath.resolve(uniqueFilename);
            file.transferTo(filePath.toFile());
            log.info("파일 저장 완료: {}", filePath.toAbsolutePath());

            String imageUrl = baseUrl + "/uploads/" + uniqueFilename;

            Map<String, String> response = new HashMap<>();
            response.put("imageUrl", imageUrl);
            response.put("filename", uniqueFilename);
            response.put("originalFilename", originalFilename);

            log.info("이미지 업로드 성공: {}", imageUrl);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("파일 업로드 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "파일 업로드 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
}