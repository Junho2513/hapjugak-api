package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.config.LoginAttemptGuard;
import com.example.hapjugakapi.dto.GoogleLoginRequest;
import com.example.hapjugakapi.dto.LoginRequest;
import com.example.hapjugakapi.dto.TokenResponse;
import com.example.hapjugakapi.exception.UnauthorizedException;
import com.example.hapjugakapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    private final LoginAttemptGuard loginAttemptGuard;

    public AuthController(AuthService authService, LoginAttemptGuard loginAttemptGuard) {
        this.authService = authService;
        this.loginAttemptGuard = loginAttemptGuard;
    }

    /**
     * 시도 제한을 서비스가 아니라 여기서 거는 이유:
     * 기준 중 하나가 IP 인데, IP 는 HTTP 의 개념이다.
     * 서비스가 HttpServletRequest 를 알게 되면 그때부터 테스트할 때마다 가짜 요청을 만들어야 한다.
     */
    @Operation(summary = "자체 로그인",
            description = "응답의 accessToken 을 오른쪽 위 Authorize 에 넣으면 이 화면에서 바로 호출해볼 수 있습니다. "
                    + "실패가 잦으면 잠시 막힙니다(429).")
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호가 올바르지 않습니다")
    @ApiResponse(responseCode = "429", description = "로그인 시도가 너무 많습니다. Retry-After 헤더에 남은 시간이 담깁니다")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = clientIp(http);
        loginAttemptGuard.checkAllowed(request.email(), ip);
        try {
            TokenResponse issued = authService.login(request);
            loginAttemptGuard.recordSuccess(request.email());
            return issued;
        } catch (UnauthorizedException e) {
            // 가입 안 된 주소든 비밀번호가 틀렸든 똑같이 센다.
            // 한쪽만 세면 "세어지는가"로 계정 존재 여부를 알아낼 수 있다.
            loginAttemptGuard.recordFailure(request.email(), ip);
            throw e;
        }
    }

    @Operation(summary = "구글 로그인",
            description = "구글이 준 인가 코드를 보내면 서버가 구글과 교환해 ID 토큰을 받고, "
                    + "서명을 검증한 뒤 우리 토큰을 돌려줍니다.")
    @PostMapping("/google")
    public TokenResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.googleLogin(request);
    }

    /**
     * 실제 접속자 IP.
     *
     * nginx 가 X-Forwarded-For 를 `$remote_addr` 로 **덮어쓰도록** 설정해 뒀다.
     * 그래서 값이 하나뿐이고, 클라이언트가 보낸 것은 nginx 에서 버려진다.
     *
     * 이 전제가 중요하다. nginx 기본값인 `$proxy_add_x_forwarded_for` 는 클라이언트가
     * 보낸 값 뒤에 진짜 IP 를 **덧붙인다.** 그 상태에서 맨 앞 값을 쓰면
     * 공격자가 헤더를 위조해 매 요청마다 다른 IP 인 척할 수 있고, IP 기준 제한이 무력해진다.
     *
     * 스프링도 forward-headers-strategy 설정에 따라 이 헤더를 읽어 getRemoteAddr() 를 채운다.
     * 값이 하나뿐이면 둘 다 같은 답을 내므로 어느 쪽을 봐도 된다.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            return first.trim();
        }
        return request.getRemoteAddr();
    }
}
