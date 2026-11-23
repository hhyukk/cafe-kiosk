package com.cafekiosk.support;

import com.cafekiosk.auth.entity.OwnerRole;
import com.cafekiosk.auth.jwt.AccessTokenIssuer;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 점주 API 를 부르는 MockMvc 요청에 점주 토큰을 싣는다.
 *
 * ── 흉내가 아니라 실제로 서명한 토큰이다 ──────────────────────────────────────────
 *
 * 운영 코드의 AccessTokenIssuer 가 운영과 같은 방식으로 서명한 토큰을 Authorization 헤더에 싣는다.
 * 요청은 보안 필터의 서명 검증, 만료 검증, 역할 확인을 그대로 지난다.
 *
 * @WithMockUser 를 쓰지 않는 이유가 둘이다. 그 어노테이션은 보안 컨텍스트를 미리 채워 두는 흉내라
 * 토큰 검증을 건너뛴다. 그리고 붙인 메서드 전체를 인증된 상태로 만들어서, 같은 테스트 안에서
 * 준비 단계로 부르는 손님 API 까지 인증된 채 지나간다. 지금은 손님 API 를 부르는 많은 호출이
 * 그 API 가 열려 있다는 사실을 덤으로 지키고 있는데, 그 덤이 사라진다.
 *
 * ── 점주 API 요청에만 싣는다 ──────────────────────────────────────────────────────
 *
 * 위의 덤을 지키기 위한 규칙이다. 손님 API 를 부르는 요청에는 붙이지 않는다.
 *
 * 토큰의 이메일은 DB 에 없는 계정이어도 된다. 토큰 검증은 서명과 만료만 보고 DB 를 다시 읽지
 * 않는다. 로그인 자체를 검증하는 것은 AuthControllerTest 다.
 */
public final class OwnerToken {

    // 메뉴 등록자가 토큰에서 온다는 것을 테스트가 이 값과 비교해 확인하므로 연다
    public static final String 테스트_점주_이메일 = "owner@test.com";

    private OwnerToken() {
    }

    public static RequestPostProcessor 점주_토큰(AccessTokenIssuer accessTokenIssuer) {
        String token = accessTokenIssuer.issue(테스트_점주_이메일, OwnerRole.ROLE_OWNER);
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }
}
