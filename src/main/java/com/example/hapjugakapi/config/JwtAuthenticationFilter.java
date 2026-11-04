package com.example.hapjugakapi.config;

import com.example.hapjugakapi.repository.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   MemberRepository memberRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.memberRepository = memberRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.isValid(token)) {
            Long memberId = jwtTokenProvider.getMemberId(token);
            String role = jwtTokenProvider.getRole(token);

            /*
             * 탈퇴한 회원의 토큰은 여기서 끊는다.
             *
             * 토큰은 발급된 순간 혼자 설 수 있게 만들어져 있어서, 탈퇴해도
             * 만료(2시간)까지는 그대로 통한다. 그 사이에 자리를 다시 잡거나
             * 남의 곡을 고칠 수 있었다. 토큰을 거둬들일 방법이 없으므로
             * 쓰이는 쪽에서 확인한다.
             *
             * 요청마다 DB 를 한 번 더 보는 비용이 생긴다. 기본키 하나로 세는
             * 질의라 가볍고, "탈퇴했는데 두 시간 동안 활동할 수 있다"를
             * 그대로 두는 것보다는 낫다고 봤다.
             *
             * 토큰 무효화 목록(블랙리스트)을 Redis 에 두면 DB 를 안 봐도 되지만,
             * 아직 Redis 가 없다. 들어오면 그때 옮긴다.
             */
            if (!memberRepository.existsByIdAndDeletedAtIsNull(memberId)) {
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            memberId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length());
        }
        return null;
    }
}