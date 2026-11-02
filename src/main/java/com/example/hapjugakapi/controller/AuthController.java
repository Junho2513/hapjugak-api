package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.dto.GoogleLoginRequest;
import com.example.hapjugakapi.dto.LoginRequest;
import com.example.hapjugakapi.dto.TokenResponse;
import com.example.hapjugakapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "로그인해서 토큰을 받습니다")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "자체 로그인", description = "응답의 accessToken 을 오른쪽 위 Authorize 에 넣으면 이 화면에서 바로 호출해볼 수 있습니다.")
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호가 올바르지 않습니다")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "구글 로그인", description = "구글이 발급한 ID 토큰을 보내면 서버가 구글 공개키로 서명을 검증하고 우리 토큰을 돌려줍니다.")
    @PostMapping("/google")
    public TokenResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.googleLogin(request);
    }
}