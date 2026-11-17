package com.cafekiosk.auth.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * 점주 토큰을 서명하고 검증하는 키, 그 키를 쓰는 인코더와 디코더를 만든다.
 *
 * ── HS256 하나로 못 박는다 ──────────────────────────────────────────────────────
 *
 * 서명하는 쪽과 검증하는 쪽이 같은 서버라 비밀키 하나를 나눠 쓰는 HMAC 이면 충분하다.
 * 공개키 방식은 검증을 다른 서버에 맡길 때 값어치가 있는데 여기에는 그런 서버가 없다.
 *
 * 디코더가 받아들이는 알고리즘을 HS256 하나로 고정하는 것은 방어다. 토큰 헤더의 alg 를 믿고
 * 검증 방식을 고르는 구현은 alg 를 none 으로 바꾼 토큰에 속는다. 여기서는 헤더가 무엇을
 * 주장하든 이 키로 만든 HS256 서명이 아니면 401 이다.
 *
 * ── 키는 32바이트 이상 ─────────────────────────────────────────────────────────
 *
 * HS256 은 SHA-256 위의 HMAC 이라 키가 256비트보다 짧으면 키가 서명의 약한 고리가 된다.
 * 라이브러리에 맡겨 두면 짧은 키가 언제 드러나는지가 그쪽 구현에 달린다. 기동하는 순간 여기서
 * 재 두면 그 물음이 없어지고, 설정이 틀린 서버는 아예 뜨지 않는다.
 *
 * base64 로 받는 이유는 키가 사람이 고른 문장이 아니라 난수여야 해서다. 난수 바이트는 그대로
 * 적을 수 없으니 base64 로 적고 여기서 푼다. 만드는 법은 .env.example 에 있다.
 *
 * ── 키는 커밋된 파일 어디에도 없다 ──────────────────────────────────────────────
 *
 * application.yml 은 환경 변수 JWT_SECRET 을 가리키기만 한다. 테스트는 AbstractIntegrationTest 가
 * 실행마다 새로 만든 키를 넣는다. 키를 아는 사람은 누구든 점주 토큰을 찍을 수 있기 때문이다.
 */
@Configuration
public class JwtConfig {

    private static final int MIN_KEY_BYTES = 32;

    @Bean
    public SecretKey jwtSigningKey(@Value("${cafekiosk.auth.jwt.secret}") String secret) {
        byte[] key = Base64.getDecoder().decode(secret);

        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "cafekiosk.auth.jwt.secret 은 base64 로 적은 " + MIN_KEY_BYTES
                            + "바이트 이상이어야 합니다. 지금은 " + key.length + "바이트입니다.");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * 토큰의 role 클레임을 Spring Security 의 권한으로 옮긴다.
     *
     * 기본 변환기는 scope 클레임을 읽어 SCOPE_ 를 붙인다. OAuth2 인가 서버가 발급한 토큰을 위한
     * 기본값이라 이 서버의 토큰에는 맞지 않는다. 읽을 클레임을 role 로 바꾸고 접두는 비운다.
     * 값이 이미 ROLE_OWNER 이므로 하나 더 붙이면 ROLE_ROLE_OWNER 가 되어 hasRole 이 영영 못 찾는다.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(AccessTokenIssuer.ROLE_CLAIM);
        authorities.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
