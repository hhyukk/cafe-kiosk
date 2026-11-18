package com.cafekiosk.auth.controller;

import com.cafekiosk.auth.dto.AuthDto;
import com.cafekiosk.auth.service.AuthService;
import com.cafekiosk.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth API", description = "점주 로그인을 담당하는 API 그룹입니다.")
@RequestMapping("/api/auth")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "점주 로그인",
            description = "이메일과 비밀번호로 로그인하고 액세스 토큰을 받습니다. "
                    + "점주 API 는 이 토큰을 Authorization 헤더에 Bearer 로 실어 부릅니다. "
                    + "로그아웃 엔드포인트는 없습니다. 토큰을 쿠키로 들고 있는 BFF 가 쿠키를 지우는 것으로 로그아웃합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "이메일이나 비밀번호 누락"),
            @ApiResponse(responseCode = "401", description = "이메일이 없거나 비밀번호가 틀림. 어느 쪽인지 알려 주지 않습니다")
    })
    // 200 한 갈래만 여기서 만들고 실패 응답은 전부 GlobalExceptionHandler 가 만든다
    @PostMapping("/login")
    public RsData<AuthDto.LoginResponse> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        return new RsData<>(
                "200-1",
                "로그인되었습니다.",
                authService.login(request.email(), request.password())
        );
    }
}
