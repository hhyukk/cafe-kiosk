package com.cafekiosk.global.springDoc;


import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 문서 설정.
 *
 * ── Authorize 버튼 ─────────────────────────────────────────────────────────────
 *
 * 점주 API 를 Swagger 에서 부르려면 토큰을 헤더에 실을 자리가 있어야 한다. bearer 방식을 선언하면
 * 화면 위에 Authorize 버튼이 생기고, 거기 넣은 토큰이 이후 요청의 Authorization 헤더에 실린다.
 * 넣을 토큰은 POST /api/auth/login 응답의 data.accessToken 이다.
 *
 * 요구를 전역으로 건다. 그래서 손님 API 에도 자물쇠가 그려지지만 손님 API 는 토큰을 무시한다.
 * 엔드포인트마다 따로 붙이면 SecurityConfig 의 경로 표와 같은 정보를 두 번째 자리에 두게 되고,
 * 둘이 어긋나는 날 문서가 틀린 말을 한다.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "카페 메뉴 관리 서비스",
                version = "v1.0.0",
                description = "주문, 결제, 메뉴 관리를 위한 API 서버 문서"
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class SpringDocConfig {
}
