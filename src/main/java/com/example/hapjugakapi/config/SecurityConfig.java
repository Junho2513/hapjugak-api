package com.example.hapjugakapi.config;

import com.example.hapjugakapi.repository.MemberRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider,
                          MemberRepository memberRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.memberRepository = memberRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 스프링이 만들어주던 기본 사용자를 끈다.
     *
     * UserDetailsService 빈이 하나도 없으면 Boot 가 「user」라는 계정을 하나 만들고
     * 뜰 때마다 무작위 비밀번호를 로그에 찍는다. 운영 로그에 이런 줄이 남아 있었다.
     *
     *   Using generated security password: 9b269c9b-...
     *
     * 지금 그 계정으로 들어올 입구는 없다. 폼 로그인도 HTTP Basic 도 켜두지 않았고
     * 인증은 전부 JwtAuthenticationFilter 를 지난다. 문제는 입구가 없는 이유가
     * 「설정에 그렇게 적혀 있어서」가 아니라 「지금은 안 켜져 있어서」라는 점이다.
     * 나중에 누가 httpBasic() 한 줄을 켜는 순간 그 계정이 살아난다.
     *
     * 빈을 하나 두면 Boot 의 자동 설정이 물러난다(@ConditionalOnMissingBean).
     * 자동 설정 클래스 이름을 exclude 로 적는 방법도 있지만, 그 이름은 버전에 따라
     * 바뀌고 틀리면 앱이 아예 안 뜬다. 빈을 두는 쪽이 안전하다.
     *
     * 이 서비스는 아이디·비밀번호를 UserDetailsService 로 찾지 않는다.
     * 자체 로그인은 AuthService 가 직접 확인하고 JWT 를 내준다.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("이 서비스는 UserDetailsService 를 쓰지 않습니다.");
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 헬스체크. 아래 GET /** 에도 걸리지만 일부러 따로 적는다.
                        // 그 줄이 언젠가 좁아져도 배포가 같이 멈추지 않도록,
                        // 그리고 "이건 일부러 열어둔 것"이 보이도록.
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // 로그인은 토큰이 없는 상태로 부르는 길이다.
                        // /auth/* 로 묶는 이유: 제공자가 늘 때마다 여기를 고치는 걸 잊으면
                        // 새 제공자만 401 이 나고, 그 원인을 찾는 데 한참 걸린다.
                        // 이 아래에 로그인 말고 다른 것을 두지 않는 것이 조건이다.
                        .requestMatchers(HttpMethod.POST, "/auth/*", "/members").permitAll()
                        .requestMatchers(HttpMethod.GET, "/members/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/members").hasRole("ADMIN")
                        // 남의 프로필은 로그인한 사람만. 이 줄이 없으면 아래 GET /** 에 걸려
                        // 누구나 /members/1, /members/2 … 로 회원 정보를 훑을 수 있다.
                        .requestMatchers(HttpMethod.GET, "/members/*").authenticated()
                        // 나머지 조회는 열어둔다. 합주 일정과 곡은 로그인 없이도 볼 수 있어야
                        // 링크를 받은 사람이 들어와 보고 가입을 결정할 수 있다.
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"status\":401,\"message\":\"로그인이 필요합니다.\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(403);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"status\":403,\"message\":\"권한이 없습니다.\"}");
                        })
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, memberRepository),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}