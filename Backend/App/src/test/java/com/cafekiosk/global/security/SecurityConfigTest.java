package com.cafekiosk.global.security;

import com.cafekiosk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경로별 권한 표를 지키는 회귀 방지선이다.
 *
 * SecurityConfig 의 공개 목록이 docs/API.md 3절 표와 어긋나는 순간을 잡는다. 점주 API 가 토큰 없이
 * 열리는 경우, 손님 경로가 잠기는 경우, 401 이 RsData 봉투를 잃는 경우다.
 *
 * ── 이 클래스가 보지 않는 것 ────────────────────────────────────────────────────
 *
 * 토큰을 실었을 때 점주 API 가 제 일을 하는지는 각 기능의 테스트가 본다. 그 테스트들이
 * support/OwnerToken 으로 실제 토큰을 싣는다. 로그인은 AuthControllerTest 가 본다.
 *
 * 테스트를 감싸는 트랜잭션이 없다. 여기서는 어떤 요청도 컨트롤러까지 가서 행을 쓰지 않고,
 * 공개 경로 확인도 읽기뿐이라 지울 것이 없다.
 */
@AutoConfigureMockMvc
public class SecurityConfigTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    // docs/API.md 3절 표에서 인증이 ROLE_OWNER 인 행 전부다. 경로 변수는 아무 값이나 넣는다.
    // 인증이 먼저 막으므로 그 메뉴나 주문이 있는지는 따지지 않는다
    static Stream<Arguments> 점주_API() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/menu"),
                Arguments.of(HttpMethod.PUT, "/api/menu/modify/1"),
                Arguments.of(HttpMethod.DELETE, "/api/menu/delete/1"),
                Arguments.of(HttpMethod.GET, "/api/orders"),
                Arguments.of(HttpMethod.PATCH, "/api/order/1/status"),
                Arguments.of(HttpMethod.PATCH, "/api/stock/1"),
                Arguments.of(HttpMethod.POST, "/api/upload/image")
        );
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("점주_API")
    @DisplayName("점주 API 를 토큰 없이 부르면 401 이고 RsData 봉투와 Bearer 요구가 함께 나간다")
    void should_점주_API는_토큰_없이_부르면_401이다(HttpMethod method, String path) throws Exception {
        mvc.perform(request(method, path))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.resultCode").value("401-1"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 클레임이 똑같아도 401 이다")
    void should_다른_키로_서명한_토큰은_401이다() throws Exception {
        // 클레임은 누구나 적을 수 있다. 서버의 키로 서명했다는 사실만이 그 클레임을 믿을 근거다
        byte[] 남의키 = new byte[32];
        new SecureRandom().nextBytes(남의키);
        JwtEncoder 남의인코더 = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(남의키, "HmacSHA256")).build();

        String 위조토큰 = 토큰(남의인코더, Instant.now().plus(1, ChronoUnit.HOURS));

        mvc.perform(get("/api/orders").header(AUTHORIZATION, "Bearer " + 위조토큰))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.resultCode").value("401-1"));
    }

    @Test
    @DisplayName("만료된 토큰은 서버의 키로 서명했어도 401 이다")
    void should_만료된_토큰은_401이다() throws Exception {
        // 디코더는 서버 사이의 시계 차이를 감안해 만료 시각에 1분 남짓 여유를 둔다.
        // 그 여유에 걸리지 않게 한 시간 전에 끝난 토큰을 만든다
        String 만료토큰 = 토큰(jwtEncoder, Instant.now().minus(1, ChronoUnit.HOURS));

        mvc.perform(get("/api/orders").header(AUTHORIZATION, "Bearer " + 만료토큰))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("401-1"));
    }

    static Stream<String> 손님_경로() {
        return Stream.of(
                "/api/menu",
                // 커밋된 시드 이미지다. 손님 화면의 메뉴 사진이 전부 이 경로로 온다
                "/uploads/Ethiopia-Yirgacheffe.jpg",
                // 점주가 토큰을 넣는 자리가 Swagger 화면이다. 그 화면이 잠기면 토큰을 넣을 길이 없다
                "/v3/api-docs",
                "/swagger-ui/index.html"
        );
    }

    @ParameterizedTest(name = "GET {0}")
    @MethodSource("손님_경로")
    @DisplayName("손님에게 여는 경로는 토큰 없이 200 이다")
    void should_공개_경로는_토큰_없이_열린다(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isOk());
    }

    // 운영 토큰과 클레임 모양이 같고 서명 키와 만료 시각만 고를 수 있는 토큰
    private String 토큰(JwtEncoder encoder, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("owner@test.com")
                .issuedAt(expiresAt.minus(12, ChronoUnit.HOURS))
                .expiresAt(expiresAt)
                .claim("role", "ROLE_OWNER")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
