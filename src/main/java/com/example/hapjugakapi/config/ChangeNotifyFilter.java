package com.example.hapjugakapi.config;

import com.example.hapjugakapi.service.StreamService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 쓰기 요청이 성공하면 접속 중인 브라우저에 "바뀌었다"고 알린다.
 * 알림을 서비스 14곳에 흩뿌리는 대신 여기 한 곳에서 처리한다.
 * 트랜잭션은 서비스 메서드가 끝날 때 이미 커밋됐으므로,
 * 알림을 받고 다시 조회하면 반드시 새 데이터가 보인다.
 */
@Component
public class ChangeNotifyFilter extends OncePerRequestFilter {

    private final StreamService streamService;

    public ChangeNotifyFilter(StreamService streamService) {
        this.streamService = streamService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        chain.doFilter(request, response);

        if (isWrite(request.getMethod()) && isSuccess(response.getStatus())) {
            streamService.publishChanged();
        }
    }

    private boolean isWrite(String method) {
        return "POST".equals(method) || "PATCH".equals(method)
                || "PUT".equals(method) || "DELETE".equals(method);
    }

    private boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    /** 로그인 요청은 데이터를 바꾸지 않으므로 알릴 필요가 없다. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().contains("/auth/");
    }
}