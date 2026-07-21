package com.cafekiosk.file.controller;

import com.cafekiosk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-FILE-07 회귀 방지선.
 *
 * 업로드 응답의 imageUrl 은 프론트가 그대로 메뉴 등록에 실어 보내므로
 * Menu.imgUrl 컬럼에 저장된다. 여기에 호스트가 붙으면 배포 환경 주소가 데이터에 박히고,
 * 코드의 하드코딩을 전부 걷어내도 이미 저장된 행은 남는다.
 * 그래서 "호스트를 포함하지 않는다"를 테스트로 못박는다.
 *
 * upload-dir 을 build/ 아래로 돌린 이유:
 * 기본값이 ./uploads 라 테스트가 실제 업로드 폴더에 파일을 흘린다.
 * build/ 는 gitignore 대상이고 clean 으로 지워진다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "file.upload-dir=build/test-uploads")
public class FileUploadControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("이미지를 업로드하면 호스트가 없는 상대경로 URL 을 돌려준다")
    void should_업로드_응답_URL은_호스트를_포함하지_않는다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "예가체프.png",
                MediaType.IMAGE_PNG_VALUE,
                "가짜 이미지 바이트".getBytes()
        );

        mvc.perform(multipart("/api/upload/image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(startsWith("/uploads/")))
                // UUID + 확장자. http:// 나 localhost 가 섞여 들어오면 여기서 걸린다.
                .andExpect(jsonPath("$.imageUrl").value(
                        matchesPattern("/uploads/[0-9a-f-]{36}\\.png")))
                .andExpect(jsonPath("$.originalFilename").value("예가체프.png"));
    }
}
