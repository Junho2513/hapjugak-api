package com.example.hapjugakapi.dto;

import com.example.hapjugakapi.domain.Event;
import com.example.hapjugakapi.domain.Part;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 생성과 수정에 함께 쓴다.
 * 수정(PATCH)에서는 null인 항목을 "바꾸지 않음"으로 해석하므로,
 * 숫자와 참·거짓도 int/boolean이 아닌 Integer/Boolean으로 받는다.
 */
public record EventRequest(
        @NotBlank(groups = OnCreate.class, message = "합주 이름을 입력해주세요.")
        @Size(max = 60, message = "합주 이름은 60자까지 쓸 수 있습니다.")
        String title,

        @NotNull(groups = OnCreate.class, message = "합주 날짜를 정해주세요.")
        LocalDate date,

        LocalDate deadline,
        LocalTime timeStart,
        LocalTime timeEnd,

        @Size(max = 30, message = "지역이 너무 깁니다.")
        String region,

        @Size(max = 100, message = "합주실 이름이 너무 깁니다.")
        String studio,

        @Size(max = 60, message = "방 이름이 너무 깁니다.")
        String room,

        @Size(max = 40, message = "장르가 너무 깁니다.")
        String genre,

        @Min(value = 0, message = "참가비는 0원 이상이어야 합니다.")
        @Max(value = 1_000_000, message = "참가비가 너무 큽니다. 금액을 다시 확인해주세요.")
        Integer feeAmount,

        @Size(max = 200, message = "비용 안내가 너무 깁니다.")
        String feeNote,

        @Size(max = 2000, message = "설명은 2000자까지 쓸 수 있습니다.")
        String description,

        @Size(max = 40, message = "뒤풀이 안내가 너무 깁니다.")
        String afterparty,

        @Min(value = 1, message = "수용 인원은 1명 이상이어야 합니다.")
        @Max(value = 200, message = "수용 인원은 200명까지 지정할 수 있습니다.")
        Integer roomCapacity,

        @Min(value = 1, message = "한 사람당 곡 수는 1곡 이상이어야 합니다.")
        @Max(value = 50, message = "한 사람당 곡 수는 50곡까지 지정할 수 있습니다.")
        Integer maxSongsPerUser,

        Event.Visibility visibility,
        List<Part> defaultParts,

        @Size(max = 500, message = "이미지 주소가 너무 깁니다.")
        String coverUrl,

        Boolean completed
) {

    /**
     * 아래 세 가지는 한 칸만 봐서는 잡을 수 없다.
     *
     * @Min 이나 @Size 는 칸 하나의 값이 말이 되는지만 본다.
     * 그런데 실제로 들어온 값 중에는 칸 하나하나는 멀쩡한데 조합이 말이 안 되는 것이 있었다.
     * 19시에 시작해서 15시에 끝나는 합주, 합주 다음날이 곡 신청 마감인 일정,
     * 96874년 9월에 열리는 합주 같은 것들이다.
     *
     * 「검증을 넣었다」와 「입력이 말이 되는지 검증했다」는 다르다.
     *
     * null 이면 전부 통과시키는 이유: 수정(PATCH)에서 null 은 "바꾸지 않음"이다.
     * 날짜만 고치려고 보낸 요청을 시각이 비어 있다고 막으면 안 된다.
     */
    private static final LocalDate 가장_이른_날 = LocalDate.of(2000, 1, 1);

    @AssertTrue(message = "곡 신청 마감일은 합주일보다 늦을 수 없습니다.")
    public boolean isDeadlineOnOrBeforeDate() {
        return deadline == null || date == null || !deadline.isAfter(date);
    }

    @AssertTrue(message = "종료 시각이 시작 시각보다 빠릅니다.")
    public boolean isTimeInOrder() {
        return timeStart == null || timeEnd == null || timeEnd.isAfter(timeStart);
    }

    @AssertTrue(message = "합주 날짜를 다시 확인해주세요.")
    public boolean isDateInRange() {
        return 말이_되는_날짜인가(date);
    }

    @AssertTrue(message = "곡 신청 마감일을 다시 확인해주세요.")
    public boolean isDeadlineInRange() {
        return 말이_되는_날짜인가(deadline);
    }

    /**
     * 위로 2년, 아래로 2000년.
     *
     * 과거를 막지 않는 이유: 지나간 합주를 기록으로 남기는 쓰임이 있다(추억 저장소).
     * 위쪽만 조이면 오타는 걸리고 정상적인 쓰임은 막지 않는다.
     */
    private static boolean 말이_되는_날짜인가(LocalDate value) {
        if (value == null) {
            return true;
        }
        return !value.isBefore(가장_이른_날) && !value.isAfter(LocalDate.now().plusYears(2));
    }
}
