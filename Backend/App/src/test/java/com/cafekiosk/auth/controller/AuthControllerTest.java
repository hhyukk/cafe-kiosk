package com.cafekiosk.auth.controller;

import com.cafekiosk.auth.entity.Owner;
import com.cafekiosk.auth.repository.OwnerRepository;
import com.cafekiosk.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 점주 로그인. 맞는 비밀번호에만 토큰이 나가고, 그 토큰이 점주 API 를 연다.
 *
 * 점주 행은 테스트 트랜잭션 안에서 만들고 함께 롤백한다. MockMvc 요청은 테스트와 같은 스레드에서
 * 돌아 그 트랜잭션에 참여하므로, 커밋하지 않은 점주 행을 로그인이 읽는다.
 */
@AutoConfigureMockMvc
@Transactional
public class AuthControllerTest extends AbstractIntegrationTest {

    private static final String 이메일 = "login@test.com";
    private static final String 비밀번호 = "correct-horse-battery";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        ownerRepository.save(new Owner(이메일, 비밀번호, passwordEncoder));
    }

    @Test
    @DisplayName("맞는 비밀번호로 로그인하면 토큰과 역할을 받는다")
    void should_로그인하면_토큰과_역할을_받는다() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(로그인본문(이메일, 비밀번호)))
                .andDo(print())
                .andExpect(handler().handlerType(AuthController.class))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.email").value(이메일))
                .andExpect(jsonPath("$.data.role").value("ROLE_OWNER"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("로그인으로 받은 토큰이 점주 API 를 연다")
    void should_로그인으로_받은_토큰으로_점주_API를_부른다() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(로그인본문(이메일, 비밀번호)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String accessToken = JsonPath.read(body, "$.data.accessToken");

        // 다른 테스트들은 AccessTokenIssuer 를 직접 불러 토큰을 얻는다. 로그인이 내준 토큰이
        // 실제로 보안 필터를 통과하는지는 여기 한곳에서만 확인된다
        mvc.perform(get("/api/orders").header(AUTHORIZATION, "Bearer " + accessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"));
    }

    @Test
    @DisplayName("비밀번호가 틀린 것과 없는 이메일은 같은 401 이라 계정이 있는지 드러나지 않는다")
    void should_계정이_있는지_응답으로_드러나지_않는다() throws Exception {
        String 틀린비밀번호 = 로그인_실패_본문(이메일, "wrong-password");
        String 없는이메일 = 로그인_실패_본문("nobody@test.com", 비밀번호);

        // 한 글자라도 다르면 아무 이메일이나 넣어 보는 것만으로 어떤 계정이 있는지 알아낼 수 있다
        assertThat(틀린비밀번호).isEqualTo(없는이메일);
        assertThat(JsonPath.<String>read(틀린비밀번호, "$.resultCode")).isEqualTo("401-1");
        assertThat(JsonPath.<String>read(틀린비밀번호, "$.message")).isEqualTo("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    private String 로그인_실패_본문(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(로그인본문(email, password)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String 로그인본문(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }
}
