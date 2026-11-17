package com.cafekiosk.auth.dto;

import com.cafekiosk.auth.entity.OwnerRole;
import jakarta.validation.constraints.NotBlank;

public class AuthDto {

    public record LoginRequest(
            @NotBlank(message = "이메일을 입력하세요.")
            String email,

            @NotBlank(message = "비밀번호를 입력하세요.")
            String password
    ) {
    }

    /**
     * 로그인 응답. 토큰을 본문에 싣는다.
     *
     * 이 응답을 받는 것은 브라우저가 아니라 BFF 다. BFF 가 accessToken 을 꺼내 httpOnly 쿠키로
     * 심고 나머지만 브라우저로 넘긴다. 토큰을 자바스크립트가 읽을 수 없는 자리에 두는 일은 그쪽 몫이다.
     *
     * 응답 헤더가 아니라 본문에 싣는 이유는 OAuth2 토큰 응답이 본문에 싣는 관례를 따르기 위해서이고,
     * 필드로 두면 타입과 Swagger 스키마가 함께 남기 때문이다.
     */
    public record LoginResponse(
            String accessToken,
            String email,
            OwnerRole role
    ) {
    }
}
