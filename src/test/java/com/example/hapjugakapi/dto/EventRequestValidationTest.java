package com.example.hapjugakapi.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 칸 하나하나는 멀쩡한데 조합이 말이 안 되는 입력을 막는지 본다.
 *
 * 실제로 운영 DB 에 들어간 값들이다.
 *   합주일 220220-02-06,  곡 신청 마감 96874-09-31,  19시 시작 15시 종료
 * @Min 이나 @Size 로는 하나도 못 잡는다. 전부 한 칸만 보는 제약이기 때문이다.
 *
 * 스프링을 띄우지 않는다. 검증기만 있으면 되는 일이다.
 */
class EventRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void 준비() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void 정리() {
        factory.close();
    }

    @Test
    @DisplayName("멀쩡한 입력은 통과한다")
    void 정상() {
        assertEquals(0, 검사(기본().build()).size());
    }

    @Test
    @DisplayName("곡 신청 마감이 합주일보다 늦으면 막는다")
    void 마감이_합주일_뒤() {
        var 위반 = 검사(기본()
                .date(LocalDate.now().plusDays(10))
                .deadline(LocalDate.now().plusDays(11))
                .build());

        assertTrue(메시지에_있나(위반, "곡 신청 마감일은 합주일보다 늦을 수 없습니다."));
    }

    @Test
    @DisplayName("합주일과 마감일이 같은 날인 것은 괜찮다")
    void 마감이_당일() {
        LocalDate 그날 = LocalDate.now().plusDays(10);

        assertEquals(0, 검사(기본().date(그날).deadline(그날).build()).size());
    }

    @Test
    @DisplayName("종료 시각이 시작 시각보다 빠르면 막는다")
    void 시각이_거꾸로() {
        var 위반 = 검사(기본()
                .timeStart(LocalTime.of(19, 0))
                .timeEnd(LocalTime.of(15, 0))
                .build());

        assertTrue(메시지에_있나(위반, "종료 시각이 시작 시각보다 빠릅니다."));
    }

    @Test
    @DisplayName("2년 넘게 뒤의 날짜는 막는다 — 오타를 잡기 위한 것이다")
    void 너무_먼_미래() {
        var 위반 = 검사(기본().date(LocalDate.now().plusYears(3)).build());

        assertTrue(메시지에_있나(위반, "합주 날짜를 다시 확인해주세요."));
    }

    @Test
    @DisplayName("지나간 날짜는 막지 않는다 — 추억 저장소에 기록으로 남긴다")
    void 과거는_허용() {
        assertEquals(0, 검사(기본()
                .date(LocalDate.now().minusMonths(6))
                .deadline(LocalDate.now().minusMonths(7))
                .build()).size());
    }

    @Test
    @DisplayName("수정할 때 보내지 않은 항목은 검사하지 않는다")
    void 비워_보낸_항목() {
        // 제목만 바꾸는 PATCH. 나머지가 전부 null 이어도 통과해야 한다.
        var 위반 = 검사(new EventRequest("이름만 바꿈", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null));

        assertEquals(0, 위반.size());
    }

    @Test
    @DisplayName("참가비는 음수가 될 수 없다")
    void 음수_참가비() {
        var 위반 = 검사(기본().feeAmount(-1000).build());

        assertTrue(메시지에_있나(위반, "참가비는 0원 이상이어야 합니다."));
    }

    private Set<ConstraintViolation<EventRequest>> 검사(EventRequest request) {
        return validator.validate(request);
    }

    private boolean 메시지에_있나(Set<ConstraintViolation<EventRequest>> 위반, String 기대) {
        return 위반.stream().anyMatch(v -> 기대.equals(v.getMessage()));
    }

    private Builder 기본() {
        return new Builder();
    }

    /**
     * record 에 인자가 19개라 테스트마다 다 적을 수가 없다.
     * 기본값을 채워 두고 보려는 항목만 바꾼다.
     */
    private static final class Builder {
        private LocalDate date = LocalDate.now().plusDays(14);
        private LocalDate deadline = LocalDate.now().plusDays(7);
        private LocalTime timeStart = LocalTime.of(19, 0);
        private LocalTime timeEnd = LocalTime.of(22, 0);
        private Integer feeAmount = 15000;

        Builder date(LocalDate v) { this.date = v; return this; }
        Builder deadline(LocalDate v) { this.deadline = v; return this; }
        Builder timeStart(LocalTime v) { this.timeStart = v; return this; }
        Builder timeEnd(LocalTime v) { this.timeEnd = v; return this; }
        Builder feeAmount(Integer v) { this.feeAmount = v; return this; }

        EventRequest build() {
            return new EventRequest(
                    "합주 한 판", date, deadline, timeStart, timeEnd,
                    "서울", "사운드바이크 홍대점", "3번 스튜디오", "록",
                    feeAmount, "대관비 N빵",
                    "처음 오셔도 괜찮습니다", "근처에서 간단히",
                    20, 2,
                    null, null, null, null
            );
        }
    }
}
