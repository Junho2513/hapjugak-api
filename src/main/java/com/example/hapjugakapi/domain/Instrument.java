package com.example.hapjugakapi.domain;

import java.util.List;
import java.util.Locale;

/**
 * 회원이 연주하는 파트.
 *
 * 전에는 여섯 가지만 담은 enum 이었다. 색소폰·트럼펫·카혼처럼 밴드에서 흔히 쓰는
 * 파트가 그 안에 없어서 적을 방법이 아예 없었고, 하나 추가할 때마다 배포가 필요했다.
 * 지금은 자유 문자열로 받고, 아래 목록은 화면에 먼저 보여줄 기본값으로만 쓴다.
 *
 * enum 을 지우는 대신 이 클래스를 둔 이유 — 다듬는 규칙을 한 곳에 모으기 위해서다.
 * 「기타」와 「기타 」와 「 기타」가 서로 다른 파트로 저장되면,
 * 목록에 같은 이름이 세 번 뜨고 아무도 그 이유를 모른다.
 */
public final class Instrument {

    /** DB 컬럼이 VARCHAR(30) 이다. 여기서 막지 않으면 DB 가 막으면서 500 이 나간다. */
    public static final int MAX_LENGTH = 30;

    /** 한 사람이 들 수 있는 파트 수. 막지 않으면 수백 개를 넣을 수 있다. */
    public static final int MAX_COUNT = 12;

    /** 화면에서 먼저 보여주는 기본 목록. 저장 규칙과는 무관하다. */
    public static final List<String> DEFAULTS =
            List.of("보컬", "통기타", "일렉기타", "베이스기타", "드럼", "건반");

    private Instrument() {
    }

    /**
     * 앞뒤 공백과 가운데 연속 공백을 정리한다.
     * 빈 문자열이나 너무 긴 값은 null 을 돌려주고, 부르는 쪽에서 걸러낸다.
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replaceAll("\\s+", " ");
        if (s.isEmpty() || s.length() > MAX_LENGTH) return null;
        return s;
    }

    /** 대소문자와 공백만 다른 값을 같은 것으로 본다. 중복 판정에만 쓴다. */
    public static String key(String normalized) {
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
