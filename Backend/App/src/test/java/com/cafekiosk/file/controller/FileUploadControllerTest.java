package com.cafekiosk.file.controller;

import com.cafekiosk.auth.jwt.AccessTokenIssuer;
import com.cafekiosk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.cafekiosk.support.OwnerToken.점주_토큰;
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
 * 검증 실패 둘이 400-1 봉투로 나가는지도 함께 본다. 크기 초과는 보지 않는다. 이유는 클래스 끝 주석에 있다.
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

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Test
    @DisplayName("이미지를 업로드하면 호스트가 없는 상대경로 URL 을 돌려준다")
    void should_업로드_응답_URL은_호스트를_포함하지_않는다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "예가체프.png",
                MediaType.IMAGE_PNG_VALUE,
                "가짜 이미지 바이트".getBytes()
        );

        mvc.perform(multipart("/api/upload/image").file(file).with(점주_토큰(accessTokenIssuer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.imageUrl").value(startsWith("/uploads/")))
                // UUID + 확장자. http:// 나 localhost 가 섞여 들어오면 여기서 걸린다.
                .andExpect(jsonPath("$.data.imageUrl").value(
                        matchesPattern("/uploads/[0-9a-f-]{36}\\.png")))
                .andExpect(jsonPath("$.data.originalFilename").value("예가체프.png"));
    }

    @Test
    @DisplayName("빈 파일을 올리면 400 이고 RsData 봉투로 나간다")
    void should_빈_파일은_400이다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "빈파일.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[0]
        );

        mvc.perform(multipart("/api/upload/image").file(file).with(점주_토큰(accessTokenIssuer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.message").value("파일이 비어있습니다"));
    }

    @Test
    @DisplayName("이미지가 아닌 파일을 올리면 400 이고 RsData 봉투로 나간다")
    void should_이미지가_아닌_파일은_400이다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "메뉴판.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "이미지가 아닌 바이트".getBytes()
        );

        mvc.perform(multipart("/api/upload/image").file(file).with(점주_토큰(accessTokenIssuer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.message").value("이미지 파일만 업로드 가능합니다"));
    }

    // 5MB 를 넘는 파일은 여기서 보지 않는다. MockMvc 는 multipart 한도를 적용하지 않아 요청이
    // 컨트롤러의 크기 검사까지 닿지만, 실제 서버에서는 그 전에 MaxUploadSizeExceededException 이 된다.
    // 여기서 400 을 보이면 실제 서버와 다른 사실을 증명하게 된다. 그 경로는 CLAUDE.md 함정표가 들고 있다.
}
