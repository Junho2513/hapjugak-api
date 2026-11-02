package com.example.hapjugakapi.config;

import com.example.hapjugakapi.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 시간이 흘러야 확인되는 규칙이라 진짜 시계로는 테스트할 수 없다.
 * (10분 잠금을 확인하려고 10분을 기다릴 수는 없다)
 * 그래서 시계를 직접 밀어가며 본다.
 *
 * 스프링을 띄우지 않는 순수 단위 테스트라 한순간에 끝난다.
 */
class LoginAttemptGuardTest {

    private static final String 이메일 = "jun@example.com";
    private static final String 아이피 = "203.0.113.7";

    private MovableClock 시계;
    private LoginAttemptGuard guard;

    @BeforeEach
    void 준비() {
        시계 = new MovableClock(Instant.parse("2026-11-02T12:00:00Z"));
        guard = new LoginAttemptGuard(시계);
    }

    @Test
    @DisplayName("네 번까지는 통과하고 다섯 번째에 막힌다")
    void 이메일_기준_제한() {
        for (int i = 0; i < 4; i++) {
            guard.recordFailure(이메일, 아이피);
            assertDoesNotThrow(() -> guard.checkAllowed(이메일, 아이피),
                    "네 번째까지는 아직 막으면 안 된다");
        }

        guard.recordFailure(이메일, 아이피);

        assertThrows(TooManyRequestsException.class,
                () -> guard.checkAllowed(이메일, 아이피));
    }

    @Test
    @DisplayName("10분이 지나면 다시 시도할 수 있다")
    void 시간이_지나면_풀린다() {
        for (int i = 0; i < 5; i++) {
            guard.recordFailure(이메일, 아이피);
        }
        assertThrows(TooManyRequestsException.class, () -> guard.checkAllowed(이메일, 아이피));

        시계.앞으로(Duration.ofMinutes(10).plusSeconds(1));

        assertDoesNotThrow(() -> guard.checkAllowed(이메일, 아이피));
    }

    @Test
    @DisplayName("성공하면 그 계정의 실패 기록은 지워진다")
    void 성공하면_초기화된다() {
        for (int i = 0; i < 4; i++) {
            guard.recordFailure(이메일, 아이피);
        }

        guard.recordSuccess(이메일);

        // 지워졌으므로 다시 네 번까지는 괜찮아야 한다
        for (int i = 0; i < 4; i++) {
            guard.recordFailure(이메일, 아이피);
            assertDoesNotThrow(() -> guard.checkAllowed(이메일, 아이피));
        }
    }

    @Test
    @DisplayName("계정을 바꿔가며 훑어도 IP 기준에 걸린다")
    void 아이피_기준_제한() {
        // 매번 다른 이메일이라 이메일 기준으로는 절대 안 걸린다
        for (int i = 0; i < 20; i++) {
            guard.recordFailure("sweep" + i + "@example.com", 아이피);
        }

        assertThrows(TooManyRequestsException.class,
                () -> guard.checkAllowed("아직안쓴주소@example.com", 아이피));
    }

    @Test
    @DisplayName("다른 IP 는 영향받지 않는다")
    void 아이피가_다르면_따로_센다() {
        for (int i = 0; i < 20; i++) {
            guard.recordFailure("sweep" + i + "@example.com", 아이피);
        }

        assertDoesNotThrow(() -> guard.checkAllowed("someone@example.com", "198.51.100.3"));
    }

    @Test
    @DisplayName("막힌 이유가 이메일 때문인지 IP 때문인지 알려주지 않는다")
    void 메시지가_정보를_흘리지_않는다() {
        for (int i = 0; i < 5; i++) {
            guard.recordFailure(이메일, 아이피);
        }

        TooManyRequestsException e = assertThrows(TooManyRequestsException.class,
                () -> guard.checkAllowed(이메일, 아이피));

        assertTrue(e.getMessage().contains("로그인 시도가 너무 많습니다"));
        assertTrue(!e.getMessage().contains(이메일) && !e.getMessage().contains(아이피),
                "메시지에 이메일이나 IP 가 들어가면 안 된다");
        assertTrue(e.getRetryAfterSeconds() > 0, "얼마나 기다려야 하는지 알려줘야 한다");
    }

    @Test
    @DisplayName("대소문자가 달라도 같은 계정으로 센다")
    void 이메일은_대소문자를_구분하지_않는다() {
        for (int i = 0; i < 5; i++) {
            guard.recordFailure("JUN@Example.COM", 아이피);
        }

        assertThrows(TooManyRequestsException.class,
                () -> guard.checkAllowed("jun@example.com", 아이피));
    }

    /** 테스트에서 원하는 만큼 앞으로 밀 수 있는 시계. */
    private static final class MovableClock extends Clock {

        private Instant now;

        private MovableClock(Instant start) {
            this.now = start;
        }

        void 앞으로(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
