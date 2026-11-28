package com.cafekiosk.file.controller;

import com.cafekiosk.global.rsData.RsData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    // 200 한 갈래만 여기서 만든다. 검증 실패는 IllegalArgumentException 으로 던지고
    // GlobalExceptionHandler 가 400-1 봉투로 옮긴다.
    //
    // 저장 중의 IOException 은 잡지 않는다. 다루지 못한 예외라 /error 로 나가 Boot 기본 500 본문이 된다.
    // 한때 여기서 잡아 예외 메시지를 응답에 그대로 실었는데, 그 메시지에는 서버의 파일 경로가 담긴다.
    // 잡아서 500-1 봉투로 바꾸는 매핑도 두지 않는다. 재고 행이 없는 500 을 다루지 않기로 한
    // docs/ADR/ADR-0003 과 어디서 갈리는지 기준을 따로 세워야 하기 때문이다.
    @PostMapping("/image")
    public RsData<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            log.warn("빈 파일 업로드 시도");
            throw new IllegalArgumentException("파일이 비어있습니다");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            log.warn("이미지가 아닌 파일 업로드 시도: {}", contentType);
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다");
        }

        long maxSize = 5 * 1024 * 1024; // 5MB
        if (file.getSize() > maxSize) {
            log.warn("파일 크기 초과: {} bytes", file.getSize());
            throw new IllegalArgumentException("파일 크기는 5MB를 초과할 수 없습니다");
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

        // 호스트를 붙이지 않는다 (FR-FILE-07).
        // 이 값은 Menu.imgUrl 컬럼에 그대로 저장되므로, 여기에 http://localhost:8080 을
        // 붙이면 배포 환경 주소가 데이터에 박힌다. 코드의 하드코딩은 grep 으로 걷어낼 수
        // 있지만 이미 저장된 행은 그렇지 않다.
        // 상대경로로 내려주면 프론트의 next.config.ts rewrite(/uploads/:path*)가 받아
        // 백엔드로 넘기므로, 브라우저가 8080 을 직접 호출하지 않게 되는 효과도 함께 얻는다.
        String imageUrl = "/uploads/" + uniqueFilename;

        Map<String, String> response = new HashMap<>();
        response.put("imageUrl", imageUrl);
        response.put("filename", uniqueFilename);
        response.put("originalFilename", originalFilename);

        log.info("이미지 업로드 성공: {}", imageUrl);
        return new RsData<>(
                "200-1",
                "이미지를 업로드하였습니다.",
                response
        );
    }
}
