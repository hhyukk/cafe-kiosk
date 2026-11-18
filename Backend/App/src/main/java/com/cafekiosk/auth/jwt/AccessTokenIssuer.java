package com.cafekiosk.auth.jwt;

import com.cafekiosk.auth.entity.OwnerRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 점주의 액세스 토큰을 찍는다.
 *
 * Spring Security 는 토큰을 검증하는 쪽만 준다. 토큰을 발급하는 엔드포인트는 없고 JwtEncoder 만
 * 준다. 그래서 어떤 클레임을 싣는지는 이 클래스가 정하고, 그것을 읽는 쪽은 JwtConfig 의 변환기다.
 * 두 자리가 같은 이름을 써야 하므로 클레임 이름을 여기 상수로 두고 변환기가 가져다 쓴다.
 *
 * ── 클레임 ────────────────────────────────────────────────────────────────────
 *
 * sub 는 이메일이다. 리소스 서버가 만든 Authentication 의 getName() 이 sub 를 돌려주므로,
 * 메뉴 등록자처럼 서버가 검증한 신원이 필요한 자리에서 이메일을 바로 꺼낸다.
 * 요청 본문의 이메일을 믿지 않고 이 값을 믿는 것이 인가의 출발점이다.
 *
 * role 은 OwnerRole 이름 그대로다. 접두를 떼거나 붙이지 않는다. 이유는 OwnerRole 주석에 있다.
 *
 * 수명은 cafekiosk.auth.jwt.expiry 다. 그 값을 왜 그렇게 잡았는지는 application.yml 에 있다.
 *
 * ── 이 클래스가 하지 않는 것 ────────────────────────────────────────────────────
 *
 * 비밀번호를 확인하지 않는다. 그건 로그인이 하고, 확인이 끝난 뒤에만 여기를 부른다.
 * 토큰을 검증하는 쪽도 서명과 만료만 보고 DB 를 다시 읽지 않는다. stateless 의 뜻이 그것이고,
 * 그래서 계정을 지워도 이미 나간 토큰은 만료까지 산다.
 */
@Component
public class AccessTokenIssuer {

    static final String ROLE_CLAIM = "role";

    private final JwtEncoder jwtEncoder;
    private final Duration expiry;

    public AccessTokenIssuer(
            JwtEncoder jwtEncoder,
            @Value("${cafekiosk.auth.jwt.expiry}") Duration expiry) {
        this.jwtEncoder = jwtEncoder;
        this.expiry = expiry;
    }

    public String issue(String email, OwnerRole role) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(email)
                .issuedAt(now)
                .expiresAt(now.plus(expiry))
                .claim(ROLE_CLAIM, role.name())
                .build();

        // 알고리즘을 헤더에 직접 적는다. 디코더가 HS256 하나만 받도록 못 박혀 있으므로
        // 발급하는 쪽도 같은 이름을 코드에 남겨 두 자리의 약속이 눈에 보이게 한다
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
