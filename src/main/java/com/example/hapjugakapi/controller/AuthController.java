package com.example.hapjugakapi.controller;

import com.example.hapjugakapi.config.LoginAttemptGuard;
import com.example.hapjugakapi.config.SocialCode;
import com.example.hapjugakapi.domain.AuthProvider;
import com.example.hapjugakapi.dto.SocialLoginRequest;
import com.example.hapjugakapi.dto.LoginRequest;
import com.example.hapjugakapi.dto.TokenResponse;
import com.example.hapjugakapi.exception.UnauthorizedException;
import com.example.hapjugakapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
     *
     * 200 을 직접 적어둔 이유: @ApiResponse 를 하나라도 쓰면 springdoc 이
     * 자동으로 넣어주던 성공 응답이 빠진다. 그대로 두면 문서가 실패 경우만 보여준다.
     */
    @Operation(summary = "자체 로그인",
            description = "응답의 accessToken 을 오른쪽 위 Authorize 에 넣으면 이 화면에서 바로 호출해볼 수 있습니다. "
                    + "실패가 잦으면 잠시 막힙니다(429).")
    @ApiResponse(responseCode = "200", description = "토큰을 발급했습니다",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TokenResponse.class)))
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

    /**
     * 소셜 로그인 — 구글·카카오·네이버.
     *
     * 제공자를 경로에 둔다. 본문에 넣는 방법도 있지만, 길이 나뉘어 있는 편이
     * 문서에서 셋이 따로 보이고 나중에 한 곳만 막기도 쉽다.
     *
     * enum 으로 바로 받지 않고 문자열로 받아 손으로 바꾼다.
     * 스프링의 기본 enum 변환은 대소문자를 가려서 /auth/google 이 400 이 되고,
     * 전역 변환기를 끼우면 이 길 말고 다른 곳의 enum 바인딩까지 같이 바뀐다.
     *
     * LOCAL 은 enum 에 있으므로 따로 막는다 — 자체 로그인은 /auth/login 의 일이다.
     */
    @Operation(summary = "소셜 로그인",
            description = "google · kakao · naver 중 하나를 길에 넣습니다. "
                    + "제공자가 준 인가 코드를 보내면 서버가 제공자와 교환해 누구인지 확인하고 "
                    + "우리 토큰을 돌려줍니다. state 는 네이버만 씁니다.")
    @ApiResponse(responseCode = "200", description = "토큰을 발급했습니다",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "401",
            description = "로그인에 실패했습니다. 같은 이메일의 계정이 이미 있는데 "
                    + "제공자가 그 주소를 확인해 주지 않은 경우도 여기에 걸립니다")
    @PostMapping("/{provider}")
    public TokenResponse socialLogin(@PathVariable String provider,
                                     @Valid @RequestBody SocialLoginRequest request) {
        return authService.socialLogin(
                socialProviderOf(provider),
                new SocialCode(request.code(), request.state()));
    }

    /**
     * 로그인한 사람이 자기 계정에 소셜 계정을 잇는다.
     *
     * 로그인과 길을 나눈 이유 — 하는 일이 다르다. 로그인은 "누구인지 알아내서
     * 토큰을 준다"이고, 이쪽은 "이미 누구인지 아는 사람에게 들어오는 길을
     * 하나 더 준다"이다. 토큰을 돌려주지 않는 것도 그래서다.
     *
     * 네이버 때문에 필요한 길이다. 네이버는 이메일이 본인 확인을 거친 것인지
     * 알려주지 않아서 이메일만으로는 자동 연결을 할 수 없다. 그런데 이미
     * 로그인한 사람이라면 본인임이 증명돼 있으므로 안전하게 붙일 수 있다.
     */
    @Operation(summary = "소셜 계정 연결",
            description = "로그인한 상태에서 google · kakao · naver 계정을 내 계정에 잇습니다. "
                    + "같은 이메일을 쓰는 다른 제공자를 한 계정으로 모을 때 씁니다.")
    @ApiResponse(responseCode = "204", description = "연결했습니다")
    @ApiResponse(responseCode = "409", description = "그 계정이 이미 다른 회원에게 연결돼 있습니다")
    @PostMapping("/link/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void link(@PathVariable String provider,
                     @Valid @RequestBody SocialLoginRequest request,
                     @AuthenticationPrincipal Long memberId) {
        authService.linkTo(memberId, socialProviderOf(provider),
                new SocialCode(request.code(), request.state()));
    }

    @Operation(summary = "소셜 계정 연결 끊기",
            description = "마지막 로그인 방법은 끊을 수 없습니다. 끊고 나면 들어올 길이 없어집니다.")
    @ApiResponse(responseCode = "204", description = "끊었습니다")
    @ApiResponse(responseCode = "409", description = "마지막 로그인 방법입니다")
    @DeleteMapping("/link/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable String provider,
                       @AuthenticationPrincipal Long memberId) {
        authService.unlink(memberId, socialProviderOf(provider));
    }

    @Operation(summary = "내 소셜 연결 목록",
            description = "회원 정보 화면에서 어느 계정이 연결돼 있는지 보여줄 때 씁니다.")
    @GetMapping("/links")
    public List<AuthProvider> links(@AuthenticationPrincipal Long memberId) {
        return authService.linkedProviders(memberId);
    }

    private static AuthProvider socialProviderOf(String name) {
        AuthProvider provider;
        try {
            provider = AuthProvider.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("지원하지 않는 로그인 방법입니다.");
        }
        if (provider == AuthProvider.LOCAL) {
            throw new UnauthorizedException("자체 로그인은 /auth/login 을 쓰세요.");
        }
        return provider;
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
