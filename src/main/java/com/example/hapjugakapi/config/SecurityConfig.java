package com.example.hapjugakapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/google", "/members").permitAll()
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
                        new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}