package com.cafekiosk.auth.exception;

/**
 * 로그인에 실패했을 때 발생한다. 이메일이 없는 경우와 비밀번호가 틀린 경우를 하나로 묶는다.
 *
 * 둘을 다른 예외로 나누면 응답도 언젠가 갈라진다. 이메일이 없다는 응답과 비밀번호가 틀렸다는
 * 응답이 다르면, 아무 이메일이나 넣어 보는 것만으로 어떤 계정이 있는지 알아낼 수 있다.
 * 메시지에도 어느 쪽인지 적지 않는다.
 *
 * Spring Security 의 AuthenticationException 을 상속하지 않는다. 그 계열은 보안 필터에서 온
 * 인증 실패를 401 로 옮기는 핸들러가 받는데, 로그인 실패가 거기 섞이면 토큰이 없다는 응답과
 * 같은 메시지로 나간다. 로그인은 이 예외 하나만 던지고 핸들러도 이 예외만 따로 받는다.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}
