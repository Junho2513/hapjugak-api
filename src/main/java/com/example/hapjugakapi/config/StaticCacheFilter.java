package com.example.hapjugakapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 화면 파일에 캐시 지시를 붙인다.
 *
 * <p>배포한 뒤에도 컴퓨터 브라우저에만 옛 화면이 계속 보이던 문제가 있었다.
 * 휴대폰은 처음 받는 것이라 멀쩡했고, 같은 파일인데 둘이 달라 보였다.
 *
 * <p>원인은 브라우저의 "알아서 하는 캐시"다. 서버가 캐시에 대해 아무 말도 하지 않으면
 * 브라우저는 Last-Modified 를 보고 유효 기간을 <b>스스로 지어낸다</b>
 * (흔히 마지막 수정 이후 흐른 시간의 10%). 오래된 파일일수록 더 오래 묻지 않는다.
 *
 * <p>그래서 명시한다.
 * <ul>
 *   <li>문서(index.html 등)는 {@code no-cache} — 캐시에 두되 쓰기 전에 반드시 서버에 묻는다.
 *       안 바뀌었으면 304 라 사실상 공짜고, 바뀌었으면 그 자리에서 새 화면이 나온다.</li>
 *   <li>폰트·이미지는 길게 둔다. 내용이 바뀌지 않는데 2MB 폰트를 매번 받을 이유가 없다.</li>
 * </ul>
 *
 * <p>이 서버는 화면과 API 가 같은 {@code /api} 아래에 있어서 둘을 주소로 가릴 수 없다.
 * 그래서 확장자 없는 주소는 전부 {@code no-cache} 가 되는데, API 응답도 캐시되면 안 되므로
 * 그대로 두면 맞다.
 *
 * <p>파일 이름에 해시를 붙이는 방식이 더 깔끔하지만, 지금은 빌드 단계 없이
 * index.html 한 장을 그대로 올리는 구조라 여기서 막는 편이 맞다.
 */
@Component
public class StaticCacheFilter extends OncePerRequestFilter {

    private static final String[] LONG_LIVED =
            {".woff2", ".woff", ".ttf", ".png", ".jpg", ".jpeg", ".webp", ".svg", ".ico"};

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isLongLived(path)) {
            response.setHeader("Cache-Control", "public, max-age=2592000, immutable");  // 30일
        } else if (isDocument(path)) {
            response.setHeader("Cache-Control", "no-cache");
        }

        chain.doFilter(request, response);
    }

    private boolean isLongLived(String path) {
        String lower = path.toLowerCase();
        for (String ext : LONG_LIVED) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    /** 확장자가 없거나 .html 이면 문서로 본다. API 주소는 여기 걸리지 않는다. */
    private boolean isDocument(String path) {
        if (path.toLowerCase().endsWith(".html")) return true;
        int slash = path.lastIndexOf('/');
        String last = slash < 0 ? path : path.substring(slash + 1);
        return last.indexOf('.') < 0;
    }
}
