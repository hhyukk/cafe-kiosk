package com.cafekiosk.global.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 누가 무엇을 부를 수 있는지를 정하는 자리. 경로별 권한은 여기 한곳에만 있다.
 * 컨트롤러와 서비스는 호출자가 누구인지 따지지 않는다.
 *
 * global 에 두는 이유는 이 표가 menu, order, stock, file 의 경로를 전부 다루기 때문이다.
 * 토큰의 모양은 auth/jwt 가 정하고 여기서는 그 토큰으로 무엇을 열지만 정한다.
 *
 * ── 잠긴 것이 기본이다 ──────────────────────────────────────────────────────────
 *
 * 손님에게 여는 경로만 이름을 적고 나머지는 전부 점주 전용이다. 새 엔드포인트를 만들고 여기를
 * 잊으면 401 이 나며 곧바로 드러난다. 반대로 잠글 경로를 적는 방식이면 잊었을 때 아무 일도
 * 일어나지 않고 열린 채로 배포된다. 실수가 시끄러운 쪽을 고른다.
 *
 * 공개 목록은 docs/API.md 3절 표의 인증 없음 행과 같다. 메서드까지 맞춘다. /api/menu 는 GET 만
 * 손님 것이고 POST 는 점주 것이다. /api/order/list 는 이메일로 남의 주문을 통째 조회할 수 있는
 * 경로라 대기번호 조회가 생기면 사라질 자리지만, 그때까지는 손님 화면이 쓰므로 연다.
 * 이 목록과 표가 어긋나는 순간은 SecurityConfigTest 가 잡는다.
 *
 * ERROR 디스패치를 여는 이유. 인가 필터는 요청마다 한 번이 아니라 디스패치마다 돈다.
 * 손님 API 가 처리하지 못한 예외를 던지면 컨테이너가 /error 로 다시 디스패치하는데, 그 디스패치가
 * 막히면 500 이 아니라 401 이 나간다. 재고 행 없는 메뉴를 주문했을 때 일부러 드러내는 500 이
 * 인증 문제로 둔갑하는 셈이다. 바깥에서 /error 를 직접 부르는 요청은 디스패치가 달라 여전히 잠겨 있다.
 *
 * ── 끄는 것 셋 ────────────────────────────────────────────────────────────────
 *
 * 세션. 토큰이 신원을 들고 오므로 서버가 기억할 것이 없다.
 *
 * CSRF. CSRF 는 브라우저가 쿠키를 저절로 실어 보내는 성질을 노린다. 이 서버는 쿠키가 아니라
 * Authorization 헤더로 자격을 받고, 헤더는 브라우저가 저절로 싣지 않는다. 방어가 필요 없어진
 * 것이 아니라 자리가 옮겨 간다. 토큰을 쿠키로 들고 있는 것은 BFF 이므로 CSRF 는 거기서 막는다.
 *
 * 기본 LogoutFilter. 세션을 비우고 /login?logout 으로 보내는 폼 로그인용이다. 지울 세션이 없는
 * 이 서버에 남겨 두면 /logout 이 302 를 내는 엉뚱한 엔드포인트 하나만 생긴다. 로그아웃은
 * BFF 가 쿠키를 지우는 것으로 성립하고 백엔드에는 엔드포인트가 없다. 판단은 docs/ADR/ADR-0006 에 있다.
 *
 * ── 401 은 한 진입점이 만든다 ──────────────────────────────────────────────────
 *
 * 토큰이 없을 때와 토큰이 깨졌을 때는 서로 다른 필터에서 걸린다. 앞엣것은 인가 필터가 익명
 * 요청을 막고 exceptionHandling 의 진입점을 부르고, 뒤엣것은 Bearer 토큰 필터가 검증에 실패해
 * oauth2ResourceServer 의 진입점을 부른다. 두 자리에 같은 진입점을 건다. 하나만 걸면 다른 쪽은
 * 프레임워크의 기본 응답을 따르는데, 그 응답은 요청의 Accept 에 따라 본문 없는 401 이거나 403 이다.
 * 어느 쪽이든 RsData 봉투가 아니다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            RsDataAuthenticationEntryPoint authenticationEntryPoint) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                // WebConfig 의 CORS 설정을 그대로 쓴다. 보안 필터가 MVC 보다 먼저 돌므로 여기서 켜지
                // 않으면 Authorization 헤더가 없는 프리플라이트가 인증 없는 요청으로 막힌다
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/menu").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/order", "/api/order/list").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().hasRole("OWNER"))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint));

        return http.build();
    }

    /**
     * 비밀번호 해시. 해시를 만드는 자리는 Owner 생성자 하나이고 이 빈은 거기 넘겨진다.
     *
     * BCrypt 를 직접 쓴다. DelegatingPasswordEncoder 는 해시 앞에 {bcrypt} 같은 알고리즘 표시를
     * 붙여 나중에 알고리즘을 갈아 끼울 길을 열어 두지만, 알고리즘이 하나뿐인 지금은 얻는 것이 없다.
     * docs/ERD.md 가 password 컬럼을 BCrypt 해시 60자 기준으로 설명한 것과도 맞춘다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
