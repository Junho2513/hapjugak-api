package com.example.hapjugakapi.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 파트 <b>이름</b>과 <b>종류</b> 사이를 오간다.
 *
 * <p>자리는 이름으로 다룬다. 「보컬1」, 「기타2」, 「색소폰」처럼 사람이 적은 그대로다.
 * 하지만 "한 사람이 같은 곡에서 기타와 베이스를 동시에 잡을 수는 없다"를 판단하려면
 * 그 자리가 어떤 악기인지는 알아야 한다. 이름에서 그걸 짐작하는 것이 여기 하는 일이다.
 *
 * <p>짐작이 빗나가도 괜찮게 만들었다. 모르는 이름은 종류가 {@code null} 이고,
 * 종류를 모르는 자리끼리는 겹침을 따지지 않는다. 막아야 할 것을 못 막는 쪽이
 * 멀쩡한 걸 막아버리는 쪽보다 낫다 — 직접 적은 파트를 서비스가 임의로 거절하면
 * 사람은 왜 안 되는지 알 수가 없다.
 */
public final class PartNames {

    private PartNames() {
    }

    /** 이름 앞머리가 이것으로 시작하면 그 종류로 본다. 긴 것부터 본다(통기타 → 기타). */
    private static final Map<String, Part> BY_PREFIX = new LinkedHashMap<>();

    static {
        BY_PREFIX.put("통기타", Part.ACOUSTIC);
        BY_PREFIX.put("어쿠스틱", Part.ACOUSTIC);
        BY_PREFIX.put("베이스", Part.BASS);
        BY_PREFIX.put("일렉기타", Part.GUITAR1);
        BY_PREFIX.put("기타", Part.GUITAR1);
        BY_PREFIX.put("드럼", Part.DRUM);
        BY_PREFIX.put("퍼커션", Part.DRUM);
        BY_PREFIX.put("키보드", Part.KEY1);
        BY_PREFIX.put("건반", Part.KEY1);
        BY_PREFIX.put("신디", Part.KEY1);
        BY_PREFIX.put("피아노", Part.KEY1);
        BY_PREFIX.put("보컬", Part.VOCAL);
        BY_PREFIX.put("노래", Part.VOCAL);
    }

    /** 합주 일정의 기본 편성(종류)을 곡의 자리 이름으로 바꾼다. */
    private static final Map<Part, String> DEFAULT_LABEL = Map.of(
            Part.VOCAL, "보컬",
            Part.ACOUSTIC, "통기타",
            Part.GUITAR1, "기타1",
            Part.GUITAR2, "기타2",
            Part.BASS, "베이스",
            Part.DRUM, "드럼",
            Part.KEY1, "키보드1",
            Part.KEY2, "키보드2"
    );

    /**
     * 보여줄 차례. 보컬 → 통기타 → 기타 → 베이스 → 드럼 → 키보드,
     * 그 뒤에 직접 적은 파트가 적은 순서대로 붙는다.
     * 같은 파트가 여럿이면 번호 순으로 나란히 선다(기타1, 기타2).
     */
    private static final List<String> PRESETS =
            List.of("보컬", "통기타", "기타", "베이스", "드럼", "키보드");

    /**
     * 차례는 <b>기본 버튼에 있는 이름인가</b>로만 가린다.
     * 악기가 겹치는지 보는 {@link #kindOf} 와는 다르다 — 「신디사이저」는 건반이지만
     * 기본 버튼에 없으니 직접 적은 파트로 쳐서 맨 뒤에 선다.
     */
    public static int rankOf(String label) {
        int i = PRESETS.indexOf(baseNameOf(label));
        return i < 0 ? 90 : i;
    }

    /** 이름 뒤의 번호를 뗀 밑이름. 「기타2」 → 「기타」. */
    public static String baseNameOf(String label) {
        if (label == null) return "";
        String name = label.trim();
        int i = name.length();
        while (i > 0 && Character.isDigit(name.charAt(i - 1))) i--;
        return name.substring(0, i).trim();
    }

    /** 이름 뒤에 붙은 번호. 없으면 0. */
    public static int numberOf(String label) {
        if (label == null) return 0;
        String name = label.trim();
        int i = name.length();
        while (i > 0 && Character.isDigit(name.charAt(i - 1))) i--;
        if (i == name.length()) return 0;
        try {
            return Integer.parseInt(name.substring(i));
        } catch (NumberFormatException e) {
            return 0;   // 자릿수가 터무니없이 길면 번호로 보지 않는다
        }
    }

    /**
     * 자리 이름을 보여줄 차례로 세운다.
     *
     * <p>직접 적은 파트끼리는 순위가 같다. 안정 정렬을 쓰면 적은 차례가 그대로 남는다.
     */
    public static Comparator<String> displayOrder() {
        return Comparator.comparingInt(PartNames::rankOf)
                .thenComparingInt(PartNames::numberOf);
    }

    /**
     * 이름에서 악기 종류를 짐작한다. 모르면 {@code null}.
     * 「기타2」처럼 뒤에 번호가 붙어 있어도 같은 종류로 본다.
     */
    public static Part kindOf(String label) {
        if (label == null) return null;
        String name = label.trim();
        if (name.isEmpty()) return null;
        for (Map.Entry<String, Part> e : BY_PREFIX.entrySet()) {
            if (name.startsWith(e.getKey())) return e.getValue();
        }
        return null;
    }

    /** 합주 일정의 기본 편성을 자리 이름 목록으로 편다. 적어둔 차례를 지킨다. */
    public static List<String> labelsOf(Iterable<Part> parts) {
        List<String> out = new java.util.ArrayList<>();
        if (parts == null) return out;
        for (Part p : parts) {
            String label = DEFAULT_LABEL.get(p);
            if (label != null && !out.contains(label)) out.add(label);
        }
        return out;
    }
}
