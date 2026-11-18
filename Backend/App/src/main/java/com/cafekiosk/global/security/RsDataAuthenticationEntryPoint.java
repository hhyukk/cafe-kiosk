package com.cafekiosk.global.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * 점주 API 를 토큰 없이, 또는 깨진 토큰으로 부른 요청에 401 을 낸다.
 *
 * ── 본문은 GlobalExceptionHandler 가 만든다 ─────────────────────────────────────
 *
 * 이 예외는 컨트롤러가 아니라 보안 필터에서 터진다. 필터는 DispatcherServlet 앞에 서 있어서
 * 그냥 두면 @ControllerAdvice 가 이 예외를 보지 못한다. 그래서 MVC 가 예외를 처리할 때 쓰는
 * 리졸버를 받아 직접 넘긴다. 리졸버가 GlobalExceptionHandler 의 AuthenticationException
 * 핸들러를 찾아 RsData 봉투로 401 을 쓴다.
 *
 * 여기서 JSON 을 직접 쓰지 않는 이유는 예외에서 HTTP 로의 매핑이 GlobalExceptionHandler 한곳에
 * 있어야 한다는 이 레포의 규칙 때문이다. 직접 쓰면 401 의 모양을 정하는 자리가 핸들러 밖에
 * 하나 더 생기고, 봉투가 바뀌는 날 한쪽만 바뀐다.
 *
 * ── 상태와 WWW-Authenticate 는 프레임워크에 맡긴다 ──────────────────────────────
 *
 * HTTP 는 401 응답에 WWW-Authenticate 헤더로 어떤 방식의 인증을 원하는지 밝히라고 요구한다.
 * BearerTokenAuthenticationEntryPoint 가 그 헤더를 Bearer 토큰 규약대로 쓰고, 토큰이 깨졌을 때는
 * invalid_token 같은 오류 코드까지 붙인다. 그 일을 먼저 시키고 본문은 뒤에 얹는다.
 * 그쪽은 상태와 헤더만 쓰고 본문은 쓰지 않으므로 둘이 겹치지 않는다.
 */
@Component
public class RsDataAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final HandlerExceptionResolver handlerExceptionResolver;

    // HandlerExceptionResolver 타입의 빈은 하나가 아니다. MVC 가 예외 처리에 쓰는 것은
    // handlerExceptionResolver 라는 이름의 빈이라 이름으로 고른다
    public RsDataAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {

        bearerEntryPoint.commence(request, response, authException);
        handlerExceptionResolver.resolveException(request, response, null, authException);
    }
}
