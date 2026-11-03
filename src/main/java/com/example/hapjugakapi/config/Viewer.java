package com.example.hapjugakapi.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 지금 목록을 보고 있는 사람.
 *
 * 왜 Long memberId 를 그냥 넘기지 않는가
 * - 비공개 일정을 걸러내려면 "누구인가"뿐 아니라 "관리자인가"도 필요하다.
 *   두 값을 따로 들고 다니면 호출부마다 둘을 맞춰 넘겨야 하고, 한쪽만 빠뜨리기 쉽다.
 * - 로그인 안 한 사람도 목록을 본다. null 을 넘기는 대신 anonymous() 라는 이름을 준다.
 *   조건문이 `memberId == null` 대신 `viewer.isAnonymous()` 가 되어 의도가 드러난다.
 *
 * 왜 관리자 여부를 DB 에서 다시 읽지 않는가
 * - 역할은 이미 토큰에 들어 있고, 필터가 권한으로 만들어 두었다.
 *   목록을 부를 때마다 회원을 한 번 더 조회할 이유가 없다.
 *
 * current() 는 컨트롤러에서만 부른다.
 * 서비스는 넘겨받은 Viewer 만 본다. 그래야 테스트에서 보안 컨텍스트를 꾸미지 않아도 된다.
 */
public record Viewer(Long memberId, boolean admin) {

    private static final Viewer ANONYMOUS = new Viewer(null, false);

    /** 로그인하지 않은 사람. 공개된 것만 볼 수 있다. */
    public static Viewer anonymous() {
        return ANONYMOUS;
    }

    /** 지금 요청을 보낸 사람. 토큰이 없거나 유효하지 않으면 익명이다. */
    public static Viewer current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 로그인 전에는 principal 이 "anonymousUser" 라는 문자열이다. Long 이 아니면 익명으로 본다.
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            return ANONYMOUS;
        }
        boolean admin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        return new Viewer(id, admin);
    }

    public boolean isAnonymous() {
        return memberId == null;
    }
}
